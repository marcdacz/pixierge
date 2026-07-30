package com.pixierge.api.background;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixierge.api.assets.AssetMetadataJobPayload;
import com.pixierge.api.filesystem.FilesystemWatcherHealth;
import com.pixierge.api.scans.ScanIdentityJobPayload;
import com.pixierge.api.scans.ScanJobTypes;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackgroundWorkControllerTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 7, 19, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void configReturnsInjectedWorkerSettings() {
        BackgroundWorkController controller = new BackgroundWorkController(
                new StubJobService(List.of()),
                new StubActivityRepository(List.of(), 0),
                new FilesystemWatcherHealth(),
                new ObjectMapper(),
                4,
                3,
                80,
                15,
                1500L
        );

        assertThat(controller.config()).isEqualTo(new BackgroundWorkConfigResponse(4, 3, 80, 15, 1500L));
    }

    @Test
    void healthReturnsQueueProblemsAndWatcherSnapshot() {
        FilesystemWatcherHealth watcherHealth = new FilesystemWatcherHealth();
        StubJobService jobService = new StubJobService(List.of());
        jobService.statusSummaries = List.of(new BackgroundJobStatusSummary(
                ScanJobTypes.ASSET_IDENTITY_BACKFILL,
                BackgroundJobRepository.STATUS_RUNNING,
                2,
                NOW,
                NOW.plusMinutes(1),
                NOW.plusMinutes(2)
        ));
        jobService.problemSummaries = List.of(new BackgroundJobProblemSummary(
                UUID.randomUUID(),
                ScanJobTypes.ASSET_METADATA_BACKFILL,
                "{}",
                "dead_letter",
                3,
                3,
                "ffprobe_failed",
                "ffprobe returned an error",
                NOW,
                NOW
        ));
        BackgroundWorkController controller = new BackgroundWorkController(
                jobService,
                new StubActivityRepository(List.of(), 0),
                watcherHealth,
                new ObjectMapper(),
                2,
                100,
                25,
                2000L
        );

        BackgroundWorkHealthResponse response = controller.health();

        assertThat(response.queues()).containsExactlyElementsOf(jobService.statusSummaries);
        assertThat(response.recentProblems()).containsExactlyElementsOf(jobService.problemSummaries);
        assertThat(response.watcher().status()).isEqualTo("stopped");
    }

    @Test
    void activityBoundsLimitAndCombinesActiveAndPersistedRows() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        UUID assetId = UUID.randomUUID();
        BackgroundJobRecord pendingMetadata = new BackgroundJobRecord(
                UUID.randomUUID(),
                ScanJobTypes.ASSET_METADATA_BACKFILL,
                objectMapper.writeValueAsString(new AssetMetadataJobPayload(
                        assetId,
                        UUID.randomUUID(),
                        "/photos/pending.raw",
                        null,
                        5L,
                        NOW,
                        "image/raw"
                )),
                BackgroundJobRepository.STATUS_PENDING,
                0,
                0,
                3,
                NOW,
                null,
                null,
                ScanJobTypes.ASSET_METADATA_BACKFILL,
                "metadata:" + assetId,
                null,
                null,
                null,
                NOW,
                NOW,
                null
        );
        StubActivityRepository activityRepository = new StubActivityRepository(
                List.of(new BackgroundFileActivityRow(UUID.randomUUID(), "/photos/recent.jpg", "added", NOW, "added", 8L)),
                1
        );
        BackgroundWorkController controller = new BackgroundWorkController(
                new StubJobService(List.of(pendingMetadata)),
                activityRepository,
                new FilesystemWatcherHealth(),
                objectMapper,
                2,
                100,
                25,
                2000L
        );

        BackgroundWorkActivityResponse response = controller.activity(0);

        assertThat(response.jobs()).singleElement().satisfies(job -> {
            assertThat(job.jobType()).isEqualTo(ScanJobTypes.ASSET_METADATA_BACKFILL);
            assertThat(job.fileCount()).isEqualTo(1);
        });
        assertThat(response.files()).singleElement().satisfies(file -> {
            assertThat(file.assetId()).isEqualTo(assetId);
            assertThat(file.fileName()).isEqualTo("pending.raw");
            assertThat(file.status()).isEqualTo("pending");
            assertThat(file.durationMs()).isNull();
        });
        assertThat(activityRepository.recentLimit).isEqualTo(-1);
    }

    @Test
    void filesReturnsPaginatedPersistedActivity() {
        StubActivityRepository activityRepository = new StubActivityRepository(
                List.of(new BackgroundFileActivityRow(UUID.randomUUID(), "/photos/a.jpg", "added", NOW, null, 240L)),
                51
        );
        BackgroundWorkController controller = new BackgroundWorkController(
                new StubJobService(List.of()),
                activityRepository,
                new FilesystemWatcherHealth(),
                new ObjectMapper(),
                2,
                100,
                25,
                2000L
        );

        BackgroundFileActivityPage page = controller.files(0, 50, null, null, null, null);

        assertThat(page.totalCount()).isEqualTo(51);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().fileName()).isEqualTo("a.jpg");
        assertThat(page.items().getFirst().durationMs()).isEqualTo(240L);
        assertThat(activityRepository.lastOffset).isZero();
        assertThat(activityRepository.lastLimit).isEqualTo(50);
    }

    @Test
    void clearFilesRemovesPersistedActivityOnly() {
        StubActivityRepository activityRepository = new StubActivityRepository(List.of(), 0);
        activityRepository.clearCount = 7;
        BackgroundWorkController controller = new BackgroundWorkController(
                new StubJobService(List.of()), activityRepository, new FilesystemWatcherHealth(), new ObjectMapper(), 2, 100, 25, 2000L
        );

        assertThat(controller.clearFiles()).isEqualTo(new BackgroundActivityClearResponse(7));
    }

    @Test
    void filesAppliesFiltersAndPrependsMatchingActiveRows() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        UUID jobId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID assetFileId = UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(new ScanIdentityJobPayload(
                UUID.randomUUID(),
                UUID.randomUUID(),
                rootId,
                List.of(new ScanIdentityJobPayload.ScanIdentityJobItem(
                        rootId,
                        assetFileId,
                        "/photos/match.jpg",
                        "/photos/match.jpg",
                        "match.jpg",
                        1L,
                        NOW
                )),
                null
        ));
        BackgroundJobRecord job = new BackgroundJobRecord(
                jobId,
                ScanJobTypes.ASSET_IDENTITY_BACKFILL,
                payload,
                BackgroundJobRepository.STATUS_RUNNING,
                0,
                1,
                3,
                NOW,
                NOW.plusMinutes(5),
                "worker",
                "identity:1:batch:9",
                "identity:1:batch:9",
                null,
                null,
                null,
                NOW,
                NOW,
                null
        );

        StubActivityRepository activityRepository = new StubActivityRepository(List.of(), 0);
        BackgroundWorkController controller = new BackgroundWorkController(
                new StubJobService(List.of(job)),
                activityRepository,
                new FilesystemWatcherHealth(),
                objectMapper,
                2,
                100,
                25,
                2000L
        );

        BackgroundFileActivityPage page = controller.files(
                0,
                50,
                "MATCH",
                List.of("processing", "added"),
                "2026-07-19",
                "2026-07-19"
        );

        assertThat(page.totalCount()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().status()).isEqualTo("processing");
        assertThat(page.items().getFirst().batchLabel()).isEqualTo("identity batch 9");
        assertThat(activityRepository.lastQuery).isEqualTo("match");
        assertThat(activityRepository.lastStatuses).containsExactlyInAnyOrder("processing", "added");
        assertThat(activityRepository.lastUpdatedFrom)
                .isEqualTo(OffsetDateTime.of(2026, 7, 19, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(activityRepository.lastUpdatedTo)
                .isEqualTo(OffsetDateTime.of(2026, 7, 20, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(activityRepository.lastOffset).isZero();
        assertThat(activityRepository.lastLimit).isEqualTo(49);
    }

    @Test
    void filesAcceptsIsoDateTimeUpdatedFrom() {
        StubActivityRepository activityRepository = new StubActivityRepository(List.of(), 0);
        BackgroundWorkController controller = new BackgroundWorkController(
                new StubJobService(List.of()),
                activityRepository,
                new FilesystemWatcherHealth(),
                new ObjectMapper(),
                2,
                100,
                25,
                2000L
        );

        controller.files(0, 50, null, null, "2026-07-19T11:00:00Z", null);

        assertThat(activityRepository.lastUpdatedFrom)
                .isEqualTo(OffsetDateTime.of(2026, 7, 19, 11, 0, 0, 0, ZoneOffset.UTC));
        assertThat(activityRepository.lastUpdatedTo).isNull();
    }

    @Test
    void filesIncludesActiveMetadataExtraction() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        UUID assetId = UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(new AssetMetadataJobPayload(
                assetId,
                UUID.randomUUID(),
                "/photos/metadata.jpg",
                "metadata.jpg",
                1L,
                NOW,
                "image/jpeg"
        ));
        BackgroundJobRecord job = new BackgroundJobRecord(
                UUID.randomUUID(),
                ScanJobTypes.ASSET_METADATA_BACKFILL,
                payload,
                BackgroundJobRepository.STATUS_RUNNING,
                0,
                1,
                3,
                NOW,
                NOW.plusMinutes(5),
                "worker",
                ScanJobTypes.ASSET_METADATA_BACKFILL,
                "metadata:" + assetId,
                null,
                null,
                null,
                NOW,
                NOW,
                null
        );
        BackgroundWorkController controller = new BackgroundWorkController(
                new StubJobService(List.of(job)),
                new StubActivityRepository(List.of(), 0),
                new FilesystemWatcherHealth(),
                objectMapper,
                2,
                100,
                25,
                2000L
        );

        BackgroundFileActivityPage page = controller.files(0, 25, null, null, null, null);

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.assetId()).isEqualTo(assetId);
            assertThat(item.fileName()).isEqualTo("metadata.jpg");
            assertThat(item.status()).isEqualTo("processing");
            assertThat(item.batchLabel()).isEqualTo("Metadata extraction");
        });
    }

    @Test
    void filesIgnoresMalformedActivePayloadsAndNormalizesCommaSeparatedStatuses() {
        BackgroundJobRecord malformedIdentity = new BackgroundJobRecord(
                UUID.randomUUID(),
                ScanJobTypes.ASSET_IDENTITY_BACKFILL,
                "{not-json",
                BackgroundJobRepository.STATUS_RUNNING,
                0,
                1,
                3,
                NOW,
                NOW.plusMinutes(5),
                "worker",
                "identity:broken",
                "identity:broken",
                null,
                null,
                null,
                NOW,
                NOW,
                null
        );
        BackgroundJobRecord malformedMetadata = new BackgroundJobRecord(
                UUID.randomUUID(),
                ScanJobTypes.ASSET_METADATA_BACKFILL,
                "{not-json",
                BackgroundJobRepository.STATUS_RUNNING,
                0,
                1,
                3,
                NOW,
                NOW.plusMinutes(5),
                "worker",
                ScanJobTypes.ASSET_METADATA_BACKFILL,
                "metadata:broken",
                null,
                null,
                null,
                NOW,
                NOW,
                null
        );
        StubActivityRepository activityRepository = new StubActivityRepository(
                List.of(new BackgroundFileActivityRow(UUID.randomUUID(), "/photos/archived.jpg", "failed", NOW, "failed", null)),
                1
        );
        BackgroundWorkController controller = new BackgroundWorkController(
                new StubJobService(List.of(malformedIdentity, malformedMetadata)),
                activityRepository,
                new FilesystemWatcherHealth(),
                new ObjectMapper(),
                2,
                100,
                25,
                2000L
        );

        BackgroundFileActivityPage page = controller.files(0, 25, null, List.of(" failed, pending ", ""), null, null);

        assertThat(page.items()).singleElement().satisfies(file -> {
            assertThat(file.status()).isEqualTo("failed");
            assertThat(file.fileName()).isEqualTo("archived.jpg");
        });
        assertThat(activityRepository.lastStatuses).containsExactlyInAnyOrder("failed", "pending");
    }

    @Test
    void filesRejectsMalformedUpdatedTo() {
        BackgroundWorkController controller = new BackgroundWorkController(
                new StubJobService(List.of()),
                new StubActivityRepository(List.of(), 0),
                new FilesystemWatcherHealth(),
                new ObjectMapper(),
                2,
                100,
                25,
                2000L
        );

        assertThatThrownBy(() -> controller.files(0, 25, null, null, null, "not-a-date"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid updatedTo");
    }

    private static final class StubJobService extends BackgroundJobService {

        private final List<BackgroundJobRecord> jobs;
        private List<BackgroundJobStatusSummary> statusSummaries = List.of();
        private List<BackgroundJobProblemSummary> problemSummaries = List.of();

        StubJobService(List<BackgroundJobRecord> jobs) {
            super(new BackgroundJobRepository(null), new TransactionTemplate());
            this.jobs = List.copyOf(jobs);
        }

        @Override
        public List<BackgroundJobRecord> latestJobs(int limit) {
            return jobs.stream().limit(Math.max(0, limit)).toList();
        }

        @Override
        public List<BackgroundJobStatusSummary> summarizeByTypeAndStatus() {
            return statusSummaries;
        }

        @Override
        public List<BackgroundJobProblemSummary> latestProblemJobs(int limit) {
            return problemSummaries.stream().limit(Math.max(0, limit)).toList();
        }
    }

    private static final class StubActivityRepository extends BackgroundActivityRepository {

        private final List<BackgroundFileActivityRow> items;
        private final int totalCount;
        private String lastQuery;
        private Collection<String> lastStatuses;
        private OffsetDateTime lastUpdatedFrom;
        private OffsetDateTime lastUpdatedTo;
        private int lastOffset = -1;
        private int lastLimit = -1;
        private int recentLimit = -1;
        private int clearCount;

        StubActivityRepository(List<BackgroundFileActivityRow> items, int totalCount) {
            super(null);
            this.items = new ArrayList<>(items);
            this.totalCount = totalCount;
        }

        @Override
        PersistedFileActivityPage searchFileActivity(
                String q,
                Collection<String> statuses,
                OffsetDateTime updatedFrom,
                OffsetDateTime updatedTo,
                int offset,
                int limit
        ) {
            lastQuery = q;
            lastStatuses = statuses == null ? null : Set.copyOf(statuses);
            lastUpdatedFrom = updatedFrom;
            lastUpdatedTo = updatedTo;
            lastOffset = offset;
            lastLimit = limit;
            return new PersistedFileActivityPage(items.stream().limit(Math.max(0, limit)).toList(), totalCount);
        }

        @Override
        List<BackgroundFileActivityRow> recentFileActivity(int limit) {
            recentLimit = limit;
            return items.stream().limit(Math.max(0, limit)).toList();
        }

        @Override
        int clear() {
            return clearCount;
        }
    }
}
