package com.pixierge.api.background;

import com.fasterxml.jackson.databind.ObjectMapper;
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
                80,
                15,
                1500L
        );

        assertThat(controller.config()).isEqualTo(new BackgroundWorkConfigResponse(4, 80, 15, 1500L));
    }

    @Test
    void filesReturnsPaginatedPersistedActivity() {
        StubActivityRepository activityRepository = new StubActivityRepository(
                List.of(new BackgroundFileActivityRow("/photos/a.jpg", "added", NOW, null)),
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
        assertThat(activityRepository.lastOffset).isZero();
        assertThat(activityRepository.lastLimit).isEqualTo(50);
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

    private static final class StubJobService extends BackgroundJobService {

        private final List<BackgroundJobRecord> jobs;

        StubJobService(List<BackgroundJobRecord> jobs) {
            super(new BackgroundJobRepository(null), new TransactionTemplate());
            this.jobs = List.copyOf(jobs);
        }

        @Override
        public List<BackgroundJobRecord> latestJobs(int limit) {
            return jobs.stream().limit(Math.max(0, limit)).toList();
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
    }
}
