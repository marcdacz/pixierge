package com.pixierge.api.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixierge.api.background.BackgroundJobDeferredException;
import com.pixierge.api.background.BackgroundJobRecord;
import com.pixierge.api.scans.ScanService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FilesystemChangeJobHandlerTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void filesystemChangeJobEnqueuesTargetedScan() throws Exception {
    RecordingScanEnqueuer scanEnqueuer = new RecordingScanEnqueuer();
    FilesystemChangeJobHandler handler = new FilesystemChangeJobHandler(scanEnqueuer, objectMapper);
    UUID libraryId = UUID.randomUUID();
    UUID rootId = UUID.randomUUID();
    String path = "/photos/incoming";
    String payload =
        objectMapper.writeValueAsString(
            new FilesystemChangeJobPayload(libraryId, rootId, path, "directory_created"));

    handler.handle(job(payload));

    assertThat(scanEnqueuer.libraryId).isEqualTo(libraryId);
    assertThat(scanEnqueuer.rootId).isEqualTo(rootId);
    assertThat(scanEnqueuer.path).isEqualTo(path);
  }

  @Test
  void defersWhenTheLibraryAlreadyHasAnActiveScan() throws Exception {
    UUID libraryId = UUID.randomUUID();
    FilesystemChangeJobHandler handler =
        new FilesystemChangeJobHandler(
            (ignoredLibraryId, ignoredRootId, ignoredPath) -> {
              throw new ScanService.ScanAlreadyActiveException(libraryId);
            },
            objectMapper);
    String payload =
        objectMapper.writeValueAsString(
            new FilesystemChangeJobPayload(
                libraryId, UUID.randomUUID(), "/photos/incoming", "file_created"));

    assertThatThrownBy(() -> handler.handle(job(payload)))
        .isInstanceOf(BackgroundJobDeferredException.class)
        .hasMessageContaining("A scan is already running for library " + libraryId);
  }

  private BackgroundJobRecord job(String payload) {
    OffsetDateTime now = OffsetDateTime.now();
    return new BackgroundJobRecord(
        UUID.randomUUID(),
        "filesystem-change-event",
        payload,
        "running",
        0,
        1,
        3,
        now,
        now.plusMinutes(5),
        "worker",
        "filesystem-change",
        "dedupe",
        null,
        null,
        null,
        now,
        now,
        null);
  }

  private static class RecordingScanEnqueuer
      implements FilesystemChangeJobHandler.FilesystemChangeScanEnqueuer {

    private UUID libraryId;
    private UUID rootId;
    private String path;

    @Override
    public void enqueueFilesystemChangeScan(UUID libraryId, UUID rootId, String path) {
      this.libraryId = libraryId;
      this.rootId = rootId;
      this.path = path;
    }
  }
}
