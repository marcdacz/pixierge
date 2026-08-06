package com.pixierge.api.scans;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixierge.api.background.BackgroundJobRecord;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

class ScanJobPayloadAndHandlerTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-30T00:00:00Z");

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void identityPayloadBuildsSingletonItemForLegacyPayloads() {
    UUID scanRunId = UUID.randomUUID();
    UUID libraryId = UUID.randomUUID();
    UUID rootId = UUID.randomUUID();
    UUID assetFileId = UUID.randomUUID();
    ScanIdentityJobPayload payload =
        new ScanIdentityJobPayload(
            scanRunId,
            libraryId,
            rootId,
            assetFileId,
            "/photos/one.jpg",
            "/normalized/photos/one.jpg",
            "one.jpg",
            42,
            NOW,
            "/normalized/photos");

    List<ScanIdentityJobPayload.ScanIdentityJobItem> items = payload.identityItems();

    assertThat(items)
        .containsExactly(
            new ScanIdentityJobPayload.ScanIdentityJobItem(
                rootId,
                assetFileId,
                "/photos/one.jpg",
                "/normalized/photos/one.jpg",
                "one.jpg",
                42,
                NOW));
    assertThat(payload.completionRootId()).isEqualTo(rootId);
  }

  @Test
  void identityPayloadUsesBatchItemsForCompletionRootWhenRootIsMissing() {
    UUID scanRunId = UUID.randomUUID();
    UUID libraryId = UUID.randomUUID();
    UUID rootId = UUID.randomUUID();
    List<ScanIdentityJobPayload.ScanIdentityJobItem> items =
        List.of(item(rootId, "one.jpg"), item(rootId, "two.jpg"));
    ScanIdentityJobPayload payload =
        new ScanIdentityJobPayload(scanRunId, libraryId, null, items, "/normalized/photos");

    assertThat(payload.identityItems()).isSameAs(items);
    assertThat(payload.completionRootId()).isEqualTo(rootId);
  }

  @Test
  void identityPayloadReturnsNoCompletionRootForIncompleteOrMixedItems() {
    UUID scanRunId = UUID.randomUUID();
    UUID libraryId = UUID.randomUUID();
    ScanIdentityJobPayload incomplete =
        new ScanIdentityJobPayload(
            scanRunId,
            libraryId,
            UUID.randomUUID(),
            null,
            "/photos/one.jpg",
            "/normalized/photos/one.jpg",
            "one.jpg",
            42,
            null,
            "/normalized/photos");
    ScanIdentityJobPayload mixed =
        new ScanIdentityJobPayload(
            scanRunId,
            libraryId,
            null,
            List.of(item(UUID.randomUUID(), "one.jpg"), item(UUID.randomUUID(), "two.jpg")),
            "/normalized/photos");

    assertThat(incomplete.identityItems()).isEmpty();
    assertThat(mixed.completionRootId()).isNull();
  }

  @Test
  void catalogPayloadConstructorDefaultsSubtreePath() {
    UUID scanRunId = UUID.randomUUID();
    UUID libraryId = UUID.randomUUID();
    UUID rootId = UUID.randomUUID();

    ScanCatalogJobPayload payload = new ScanCatalogJobPayload(scanRunId, libraryId, rootId);

    assertThat(payload.subtreePath()).isNull();
  }

  @Test
  void subtreeHandlerDelegatesHandleAndCompletionToScanService() throws Exception {
    RecordingScanService scanService = new RecordingScanService(objectMapper);
    ScanCatalogSubtreeJobHandler handler =
        new ScanCatalogSubtreeJobHandler(scanService, objectMapper);
    ScanCatalogJobPayload payload =
        new ScanCatalogJobPayload(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "/normalized/photos");
    BackgroundJobRecord job = job(objectMapper.writeValueAsString(payload));

    handler.handle(job);
    handler.afterComplete(job);

    assertThat(handler.jobType()).isEqualTo(ScanJobTypes.LIBRARY_CATALOG_SUBTREE);
    assertThat(scanService.executedPayloads).containsExactly(payload);
    assertThat(scanService.completedPayloads).containsExactly(payload);
    assertThat(scanService.executedJobIds).containsExactly(job.id());
  }

  private static ScanIdentityJobPayload.ScanIdentityJobItem item(UUID rootId, String fileName) {
    return new ScanIdentityJobPayload.ScanIdentityJobItem(
        rootId,
        UUID.randomUUID(),
        "/photos/" + fileName,
        "/normalized/photos/" + fileName,
        fileName,
        42,
        NOW);
  }

  private static BackgroundJobRecord job(String payloadJson) {
    return new BackgroundJobRecord(
        UUID.randomUUID(),
        ScanJobTypes.LIBRARY_CATALOG_SUBTREE,
        payloadJson,
        "running",
        0,
        1,
        3,
        NOW,
        NOW.plusMinutes(5),
        null,
        "library:1",
        "catalog:1",
        null,
        null,
        null,
        NOW,
        NOW,
        null);
  }

  private static final class RecordingScanService extends ScanService {

    private final List<ScanCatalogJobPayload> executedPayloads = new ArrayList<>();
    private final List<UUID> executedJobIds = new ArrayList<>();
    private final List<ScanCatalogJobPayload> completedPayloads = new ArrayList<>();

    private RecordingScanService(ObjectMapper objectMapper) {
      super(null, null, null, null, new TransactionTemplate(), objectMapper, null, 1);
    }

    @Override
    void executeCatalogJob(ScanCatalogJobPayload payload, UUID jobId) {
      executedPayloads.add(payload);
      executedJobIds.add(jobId);
    }

    @Override
    void tryCompleteCatalogScan(ScanCatalogJobPayload payload) {
      completedPayloads.add(payload);
    }
  }
}
