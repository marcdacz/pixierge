package com.pixierge.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixierge.api.assets.StorageProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class CatalogServiceTest {
  private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID AGGREGATE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-08-06T00:00:00Z");

  @TempDir Path storageRoot;

  @Test
  void recordSerializesPayloadWithStableKeyOrder() {
    CatalogRepository repository = mock(CatalogRepository.class);
    CatalogService service = service(repository);

    service.record(
        new CatalogChange() {
          @Override
          public String type() {
            return CatalogEventTypes.USER_CREATED;
          }

          @Override
          public int version() {
            return 1;
          }

          @Override
          public String aggregateType() {
            return "user";
          }

          @Override
          public UUID aggregateId() {
            return AGGREGATE_ID;
          }

          @Override
          public Object payload() {
            return Map.of("z", 1, "a", "first");
          }
        },
        null);

    ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
    verify(repository)
        .addEvent(
            any(),
            eq(1),
            eq("user.created"),
            eq("user"),
            eq(AGGREGATE_ID),
            eq(null),
            payload.capture());
    assertThat(payload.getValue()).isEqualTo("{\"a\":\"first\",\"z\":1}");
  }

  @Test
  void statusReportsCurrentLaggingAndLatestFailure() {
    CatalogRepository repository = mock(CatalogRepository.class);
    when(repository.newestSequence()).thenReturn(Optional.of(8L));
    when(repository.newestExportedSequence()).thenReturn(Optional.of(8L));
    when(repository.history(0, 1)).thenReturn(List.of());
    CatalogService service = service(repository);

    assertThat(service.status().status()).isEqualTo("current");

    when(repository.newestExportedSequence()).thenReturn(Optional.of(5L));
    assertThat(service.status().status()).isEqualTo("lagging");
    assertThat(service.status().pendingEventCount()).isEqualTo(3);

    when(repository.history(0, 1)).thenReturn(List.of(snapshot("failed", "disk unavailable")));
    assertThat(service.status())
        .extracting(CatalogStatusResponse::status, CatalogStatusResponse::failureDetail)
        .containsExactly("degraded", "disk unavailable");
  }

  @Test
  void historyClampsPagingAndDetectsNextPage() {
    CatalogRepository repository = mock(CatalogRepository.class);
    when(repository.history(0, 26))
        .thenReturn(List.of(snapshot("completed", null), snapshot("completed", null)));
    when(repository.snapshotCount()).thenReturn(2L);

    CatalogHistoryResponse history = service(repository).history(-2, 100);

    assertThat(history.page()).isZero();
    assertThat(history.pageSize()).isEqualTo(25);
    assertThat(history.totalCount()).isEqualTo(2L);
    assertThat(history.hasNext()).isFalse();
    verify(repository).history(0, 26);
    verify(repository).snapshotCount();
  }

  @Test
  void auditsPagedEventsAndDeletesExpiredRows() {
    CatalogRepository repository = mock(CatalogRepository.class);
    CatalogEvent event = event(4, "{\"action\":\"renamed\"}");
    when(repository.auditHistory(0, 26, "album", null, null, null)).thenReturn(List.of(event));
    when(repository.auditCount("album", null, null, null)).thenReturn(1L);
    when(repository.deleteAuditBefore(any())).thenReturn(3L);
    CatalogService service = service(repository);

    CatalogService.AuditHistoryResponse history =
        service.auditHistory(-1, 100, "album", null, null, null);

    assertThat(history)
        .extracting(
            CatalogService.AuditHistoryResponse::page,
            CatalogService.AuditHistoryResponse::pageSize,
            CatalogService.AuditHistoryResponse::totalCount,
            CatalogService.AuditHistoryResponse::hasNext)
        .containsExactly(0, 25, 1L, false);
    assertThat(history.items().getFirst())
        .extracting(
            CatalogService.AuditEventResponse::area, CatalogService.AuditEventResponse::action)
        .containsExactly("users", "user.created");
    assertThat(service.deleteExpiredAuditEvents()).isEqualTo(3L);
  }

  @Test
  void exportWritesNdjsonMarksEventsAndRecordsChecksum() throws Exception {
    CatalogRepository repository = mock(CatalogRepository.class);
    when(repository.newestSequence()).thenReturn(Optional.of(7L));
    when(repository.allEventsThrough(7)).thenReturn(List.of(event(7, "{\"b\":2,\"a\":1}")));

    CatalogSnapshotResponse response = service(repository).exportNow();

    ArgumentCaptor<CatalogSnapshot> snapshot = ArgumentCaptor.forClass(CatalogSnapshot.class);
    verify(repository).addSnapshot(snapshot.capture());
    Path file = storageRoot.resolve(snapshot.getValue().storagePath());
    assertThat(Files.readString(file)).contains("\"sequence\":7", "\"payload\":{\"a\":1,\"b\":2}");
    assertThat(response.status()).isEqualTo("completed");
    assertThat(response.checksum()).hasSize(64);
    verify(repository).markExportedThrough(7);
  }

  @Test
  void exportCreatesAnEmptyBaselineWhenNoEventsRemain() throws Exception {
    CatalogRepository repository = mock(CatalogRepository.class);
    when(repository.newestSequence()).thenReturn(Optional.empty());

    CatalogSnapshotResponse response = service(repository).exportNow();

    ArgumentCaptor<CatalogSnapshot> snapshot = ArgumentCaptor.forClass(CatalogSnapshot.class);
    verify(repository).addSnapshot(snapshot.capture());
    assertThat(response.status()).isEqualTo("completed");
    assertThat(response.throughSequence()).isZero();
    assertThat(Files.readString(storageRoot.resolve(snapshot.getValue().storagePath()))).isEmpty();
  }

  @Test
  void downloadReadsACompletedExportAfterCheckingItsIntegrity() throws Exception {
    CatalogRepository repository = mock(CatalogRepository.class);
    byte[] bytes = "catalog export".getBytes(StandardCharsets.UTF_8);
    Path relative = Path.of("catalog", "events.ndjson");
    Files.createDirectories(storageRoot.resolve(relative).getParent());
    Files.write(storageRoot.resolve(relative), bytes);
    when(repository.snapshot(EVENT_ID))
        .thenReturn(
            Optional.of(
                new CatalogSnapshot(
                    EVENT_ID,
                    CREATED_AT,
                    7,
                    relative.toString(),
                    sha256(bytes),
                    bytes.length,
                    "completed",
                    null)));

    CatalogService.CatalogExportDownload download = service(repository).download(EVENT_ID);

    assertThat(download.fileName()).isEqualTo("catalog-export-" + EVENT_ID + ".ndjson");
    assertThat(download.byteSize()).isEqualTo(bytes.length);
    assertThat(Files.readAllBytes(download.path())).isEqualTo(bytes);
  }

  @Test
  void downloadRejectsMissingOrAlteredExports() throws Exception {
    CatalogRepository repository = mock(CatalogRepository.class);
    when(repository.snapshot(EVENT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service(repository).download(EVENT_ID))
        .isInstanceOf(CatalogService.CatalogExportNotFoundException.class);

    byte[] bytes = "altered export".getBytes(StandardCharsets.UTF_8);
    Path relative = Path.of("catalog", "altered.ndjson");
    Files.createDirectories(storageRoot.resolve(relative).getParent());
    Files.write(storageRoot.resolve(relative), bytes);
    when(repository.snapshot(EVENT_ID))
        .thenReturn(
            Optional.of(
                new CatalogSnapshot(
                    EVENT_ID,
                    CREATED_AT,
                    7,
                    relative.toString(),
                    "incorrect-checksum",
                    bytes.length,
                    "completed",
                    null)));

    assertThatThrownBy(() -> service(repository).download(EVENT_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Catalog export integrity check failed");
  }

  private CatalogService service(CatalogRepository repository) {
    StorageProperties properties = new StorageProperties();
    properties.setRoot(storageRoot.toString());
    return new CatalogService(repository, new ObjectMapper(), properties);
  }

  private CatalogEvent event(long sequence, String payload) {
    return new CatalogEvent(
        sequence,
        EVENT_ID,
        1,
        "user.created",
        "user",
        AGGREGATE_ID,
        null,
        null,
        payload,
        CREATED_AT);
  }

  private CatalogSnapshot snapshot(String status, String failure) {
    return new CatalogSnapshot(
        EVENT_ID, CREATED_AT, 7, "catalog/events.ndjson", "checksum", 12, status, failure);
  }

  private String sha256(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }
}
