package com.pixierge.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixierge.api.assets.StorageProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
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

    CatalogHistoryResponse history = service(repository).history(-2, 100);

    assertThat(history.page()).isZero();
    assertThat(history.pageSize()).isEqualTo(25);
    assertThat(history.hasNext()).isFalse();
    verify(repository).history(0, 26);
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

  private CatalogService service(CatalogRepository repository) {
    StorageProperties properties = new StorageProperties();
    properties.setRoot(storageRoot.toString());
    return new CatalogService(repository, new ObjectMapper(), properties);
  }

  private CatalogEvent event(long sequence, String payload) {
    return new CatalogEvent(
        sequence, EVENT_ID, 1, "user.created", "user", AGGREGATE_ID, null, payload, CREATED_AT);
  }

  private CatalogSnapshot snapshot(String status, String failure) {
    return new CatalogSnapshot(
        EVENT_ID, CREATED_AT, 7, "catalog/events.ndjson", "checksum", 12, status, failure);
  }
}
