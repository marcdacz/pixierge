package com.pixierge.api.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pixierge.api.assets.StorageProperties;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
  private static final int HISTORY_PAGE_SIZE = 25;
  private final CatalogRepository repository;
  private final CatalogChangeRegistry changeRegistry = new CatalogChangeRegistry();
  private final ObjectMapper canonicalMapper;
  private final Path storageRoot;

  public CatalogService(
      CatalogRepository repository,
      ObjectMapper objectMapper,
      StorageProperties storageProperties) {
    this.repository = repository;
    this.canonicalMapper =
        objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    this.storageRoot = Path.of(storageProperties.getRoot()).toAbsolutePath().normalize();
  }

  @Transactional
  public void record(CatalogChange change, UUID actorId) {
    changeRegistry.validate(change);
    try {
      repository.addEvent(
          UUID.randomUUID(),
          change.version(),
          change.type(),
          change.aggregateType(),
          change.aggregateId(),
          actorId,
          canonicalMapper.writeValueAsString(change.payload()));
    } catch (IOException exception) {
      throw new IllegalStateException("Could not serialize catalog event", exception);
    }
  }

  @Transactional(readOnly = true)
  public CatalogStatusResponse status() {
    long latest = repository.newestSequence().orElse(0L);
    long exported = repository.newestExportedSequence().orElse(0L);
    long pending = Math.max(0L, latest - exported);
    CatalogSnapshot latestSnapshot = repository.history(0, 1).stream().findFirst().orElse(null);
    String failure =
        latestSnapshot != null && "failed".equals(latestSnapshot.status())
            ? latestSnapshot.failureDetail()
            : null;
    return new CatalogStatusResponse(
        failure != null ? "degraded" : pending == 0 ? "current" : "lagging",
        latest,
        exported,
        pending,
        failure);
  }

  @Transactional(readOnly = true)
  public CatalogHistoryResponse history(int page, int pageSize) {
    int safePage = Math.max(0, page);
    int safePageSize = Math.min(HISTORY_PAGE_SIZE, Math.max(1, pageSize));
    List<CatalogSnapshot> rows = repository.history(safePage * safePageSize, safePageSize + 1);
    return new CatalogHistoryResponse(
        rows.stream().limit(safePageSize).map(this::response).toList(),
        safePage,
        safePageSize,
        rows.size() > safePageSize);
  }

  @Transactional
  public CatalogSnapshotResponse exportNow() {
    long through = repository.newestSequence().orElse(0L);
    List<CatalogEvent> events = through == 0 ? List.of() : repository.allEventsThrough(through);
    UUID snapshotId = UUID.randomUUID();
    Path relative =
        Path.of(
            "catalog", "events-" + LocalDate.now(ZoneOffset.UTC) + "-" + snapshotId + ".ndjson");
    try {
      Path target = resolveStoragePath(relative);
      byte[] bytes = serialize(events).getBytes(StandardCharsets.UTF_8);
      writeAtomically(target, bytes);
      CatalogSnapshot snapshot =
          new CatalogSnapshot(
              snapshotId,
              OffsetDateTime.now(),
              through,
              relative.toString(),
              sha256(bytes),
              bytes.length,
              "completed",
              null);
      repository.addSnapshot(snapshot);
      repository.markExportedThrough(through);
      return response(snapshot);
    } catch (IOException exception) {
      CatalogSnapshot failed =
          new CatalogSnapshot(
              snapshotId,
              OffsetDateTime.now(),
              through,
              relative.toString(),
              "",
              0,
              "failed",
              sanitize(exception.getMessage()));
      repository.addSnapshot(failed);
      return response(failed);
    }
  }

  private String serialize(List<CatalogEvent> events) throws IOException {
    StringBuilder output = new StringBuilder();
    for (CatalogEvent event : events) {
      JsonNode payload = canonicalize(canonicalMapper.readTree(event.payloadJson()));
      Map<String, Object> line = new LinkedHashMap<>();
      line.put("aggregateId", event.aggregateId());
      line.put("aggregateType", event.aggregateType());
      line.put("actorUserId", event.actorUserId());
      line.put("eventId", event.eventId());
      line.put("eventType", event.eventType());
      line.put("eventVersion", event.eventVersion());
      line.put("payload", payload);
      line.put("sequence", event.sequence());
      output.append(canonicalMapper.writeValueAsString(line)).append('\n');
    }
    return output.toString();
  }

  private Path resolveStoragePath(Path relative) {
    Path resolved = storageRoot.resolve(relative).normalize();
    if (!resolved.startsWith(storageRoot) || relative.isAbsolute())
      throw new IllegalArgumentException("Catalog path escapes storage root");
    return resolved;
  }

  private JsonNode canonicalize(JsonNode node) {
    if (node.isObject()) {
      ObjectNode sorted = canonicalMapper.createObjectNode();
      TreeMap<String, JsonNode> fields = new TreeMap<>();
      node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
      fields.forEach((key, value) -> sorted.set(key, canonicalize(value)));
      return sorted;
    }
    if (node.isArray()) {
      ArrayNode normalized = canonicalMapper.createArrayNode();
      node.forEach(value -> normalized.add(canonicalize(value)));
      return normalized;
    }
    return node;
  }

  private void writeAtomically(Path target, byte[] bytes) throws IOException {
    Files.createDirectories(target.getParent());
    Path temp = Files.createTempFile(target.getParent(), ".catalog-", ".tmp");
    try (FileChannel channel = FileChannel.open(temp, java.nio.file.StandardOpenOption.WRITE)) {
      channel.write(java.nio.ByteBuffer.wrap(bytes));
      channel.force(true);
    }
    try {
      Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException ignored) {
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private String sha256(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private String sanitize(String message) {
    return message == null
        ? "Catalog export failed"
        : message.replaceAll("[\\r\\n]", " ").substring(0, Math.min(500, message.length()));
  }

  private CatalogSnapshotResponse response(CatalogSnapshot snapshot) {
    return new CatalogSnapshotResponse(
        snapshot.id(),
        snapshot.createdAt(),
        snapshot.throughSequence(),
        snapshot.byteSize(),
        snapshot.checksum(),
        snapshot.status(),
        snapshot.failureDetail());
  }
}
