package com.pixierge.api.filesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixierge.api.background.BackgroundJobCreate;
import com.pixierge.api.background.BackgroundJobRepository;
import com.pixierge.api.background.BackgroundJobService;
import com.pixierge.api.libraries.LibraryRepository;
import com.pixierge.api.scans.ScanJobTypes;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LibraryFilesystemWatcherTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void enqueueChangeCreatesDurableDebouncedFilesystemJob() throws Exception {
        RecordingBackgroundJobService backgroundJobService = new RecordingBackgroundJobService();
        LibraryFilesystemWatcher watcher = new LibraryFilesystemWatcher(
                new LibraryRepository(null),
                backgroundJobService,
                objectMapper,
                Duration.ofSeconds(2),
                Duration.ofSeconds(30)
        );
        UUID libraryId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        Path path = Path.of("/photos/incoming");

        watcher.enqueueChange(libraryId, rootId, path, "directory_created");

        BackgroundJobCreate job = backgroundJobService.enqueuedJobs.getFirst();
        FilesystemChangeJobPayload payload = objectMapper.readValue(
                job.payloadJson(),
                FilesystemChangeJobPayload.class
        );
        assertThat(job.jobType()).isEqualTo(ScanJobTypes.FILESYSTEM_CHANGE_EVENT);
        assertThat(job.priority()).isEqualTo(10);
        assertThat(job.maxAttempts()).isEqualTo(3);
        assertThat(job.concurrencyKey()).isEqualTo("filesystem-change:" + libraryId);
        assertThat(job.dedupeKey()).isEqualTo(ScanJobTypes.FILESYSTEM_CHANGE_EVENT + ":" + rootId + ":" + path);
        assertThat(payload.libraryId()).isEqualTo(libraryId);
        assertThat(payload.rootId()).isEqualTo(rootId);
        assertThat(payload.path()).isEqualTo(path.toString());
        assertThat(payload.eventType()).isEqualTo("directory_created");
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
