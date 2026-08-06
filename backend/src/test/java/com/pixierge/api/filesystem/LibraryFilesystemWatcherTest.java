package com.pixierge.api.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixierge.api.background.BackgroundJobCreate;
import com.pixierge.api.background.BackgroundJobRepository;
import com.pixierge.api.background.BackgroundJobService;
import com.pixierge.api.libraries.LibraryRepository;
import com.pixierge.api.scans.ScanJobTypes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionTemplate;

class LibraryFilesystemWatcherTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @TempDir private Path tempDir;

  @Test
  void enqueueChangeCreatesDurableDebouncedFilesystemJob() throws Exception {
    RecordingBackgroundJobService backgroundJobService = new RecordingBackgroundJobService();
    LibraryFilesystemWatcher watcher =
        new LibraryFilesystemWatcher(
            new LibraryRepository(null),
            backgroundJobService,
            objectMapper,
            new FilesystemWatcherHealth(),
            Duration.ofSeconds(2),
            Duration.ofSeconds(30));
    UUID libraryId = UUID.randomUUID();
    UUID rootId = UUID.randomUUID();
    Path path = Path.of("/photos/incoming");

    watcher.enqueueChange(libraryId, rootId, path, "directory_created");

    BackgroundJobCreate job = backgroundJobService.enqueuedJobs.getFirst();
    FilesystemChangeJobPayload payload =
        objectMapper.readValue(job.payloadJson(), FilesystemChangeJobPayload.class);
    assertThat(job.jobType()).isEqualTo(ScanJobTypes.FILESYSTEM_CHANGE_EVENT);
    assertThat(job.priority()).isEqualTo(10);
    assertThat(job.maxAttempts()).isEqualTo(25);
    assertThat(job.concurrencyKey()).isEqualTo("filesystem-change:" + libraryId);
    assertThat(job.dedupeKey())
        .isEqualTo(ScanJobTypes.FILESYSTEM_CHANGE_EVENT + ":" + rootId + ":" + path);
    assertThat(payload.libraryId()).isEqualTo(libraryId);
    assertThat(payload.rootId()).isEqualTo(rootId);
    assertThat(payload.path()).isEqualTo(path.toString());
    assertThat(payload.eventType()).isEqualTo("directory_created");
  }

  @Test
  void watcherHealthTracksOverflowAndRecoversAfterCleanRefresh() {
    FilesystemWatcherHealth health = new FilesystemWatcherHealth();

    health.recordStarted();
    health.recordOverflow("Overflow under /photos");
    FilesystemWatcherHealthSnapshot degraded = health.snapshot();
    health.recordRegistrationRefresh(1, 5, true);
    FilesystemWatcherHealthSnapshot recovered = health.snapshot();

    assertThat(degraded.status()).isEqualTo("degraded");
    assertThat(degraded.lastErrorCode()).isEqualTo("watcher_overflow");
    assertThat(degraded.lastOverflowAt()).isNotNull();
    assertThat(recovered.status()).isEqualTo("healthy");
    assertThat(recovered.lastErrorCode()).isEqualTo("watcher_overflow");
    assertThat(recovered.registeredRootCount()).isEqualTo(1);
    assertThat(recovered.registeredDirectoryCount()).isEqualTo(5);
  }

  @Test
  void startRegistersActiveLibraryDirectoriesAndStopsCleanly() throws Exception {
    Path root = tempDir.resolve("library");
    Files.createDirectories(root.resolve("nested"));
    Files.writeString(root.resolve("nested/photo.jpg"), "image");
    FilesystemWatcherHealth health = new FilesystemWatcherHealth();
    LibraryFilesystemWatcher watcher =
        new LibraryFilesystemWatcher(
            new StubLibraryRepository(library(root, "active")),
            new RecordingBackgroundJobService(),
            objectMapper,
            health,
            Duration.ofMillis(10),
            Duration.ofMillis(100));

    try {
      watcher.start();
      waitFor(() -> health.snapshot().registeredDirectoryCount() == 2);

      FilesystemWatcherHealthSnapshot snapshot = health.snapshot();
      assertThat(snapshot.status()).isEqualTo("healthy");
      assertThat(snapshot.registeredRootCount()).isEqualTo(1);
      assertThat(snapshot.registeredDirectoryCount()).isEqualTo(2);
    } finally {
      watcher.destroy();
    }

    assertThat(health.snapshot().status()).isEqualTo("stopped");
  }

  @Test
  void startReportsUnavailableActiveRootsAndIgnoresArchivedLibraries() throws Exception {
    Path missingRoot = tempDir.resolve("missing");
    RecordingBackgroundJobService backgroundJobService = new RecordingBackgroundJobService();
    FilesystemWatcherHealth health = new FilesystemWatcherHealth();
    LibraryFilesystemWatcher watcher =
        new LibraryFilesystemWatcher(
            new StubLibraryRepository(
                library(missingRoot, "active"), library(tempDir.resolve("archived"), "archived")),
            backgroundJobService,
            objectMapper,
            health,
            Duration.ZERO,
            Duration.ofMillis(100));

    try {
      watcher.start();
      waitFor(() -> "degraded".equals(health.snapshot().status()));

      FilesystemWatcherHealthSnapshot snapshot = health.snapshot();
      assertThat(snapshot.lastErrorCode()).isEqualTo("root_unavailable");
      assertThat(snapshot.registeredRootCount()).isEqualTo(1);
      assertThat(backgroundJobService.enqueuedJobs)
          .singleElement()
          .satisfies(
              job -> assertThat(job.dedupeKey()).contains(ScanJobTypes.FILESYSTEM_CHANGE_EVENT));
      FilesystemChangeJobPayload payload =
          objectMapper.readValue(
              backgroundJobService.enqueuedJobs.getFirst().payloadJson(),
              FilesystemChangeJobPayload.class);
      assertThat(payload.path()).isEqualTo(missingRoot.toString());
      assertThat(payload.eventType()).isEqualTo("root_unavailable");
    } finally {
      watcher.destroy();
    }
  }

  private static void waitFor(Condition condition) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.matches()) {
        return;
      }
      Thread.sleep(25);
    }
    throw new AssertionError("condition was not met");
  }

  private static LibraryRepository.LibraryRecord library(Path root, String status) {
    UUID libraryId = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.parse("2026-07-30T00:00:00Z");
    return new LibraryRepository.LibraryRecord(
        libraryId,
        "Photos",
        status,
        now,
        now,
        null,
        List.of(
            new LibraryRepository.LibraryRootRecord(
                UUID.randomUUID(), libraryId, root.toString(), root.toString(), now)),
        List.of());
  }

  @FunctionalInterface
  private interface Condition {
    boolean matches() throws InterruptedException;
  }

  private static class StubLibraryRepository extends LibraryRepository {

    private final List<LibraryRecord> libraries;

    StubLibraryRepository(LibraryRecord... libraries) {
      super(null);
      this.libraries = List.of(libraries);
    }

    @Override
    public List<LibraryRecord> listLibraries() {
      return libraries;
    }
  }

  private static class RecordingBackgroundJobService extends BackgroundJobService {

    private final List<BackgroundJobCreate> enqueuedJobs = new ArrayList<>();

    RecordingBackgroundJobService() {
      super(new BackgroundJobRepository(null), new TransactionTemplate());
    }

    @Override
    public UUID enqueue(BackgroundJobCreate create) {
      enqueuedJobs.add(create);
      return UUID.randomUUID();
    }
  }
}
