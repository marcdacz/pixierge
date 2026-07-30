package com.pixierge.api.background;

import com.pixierge.api.db.QBackgroundJobs;
import com.pixierge.api.db.QFileActivityEvents;
import com.pixierge.api.db.QAssetFiles;
import com.pixierge.api.db.QAssetMetadata;
import com.pixierge.api.db.QAssets;
import com.pixierge.api.db.QLibraries;
import com.pixierge.api.db.QLibraryRoots;
import com.pixierge.api.db.QUsers;
import com.querydsl.sql.SQLQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "pixierge.background-jobs.enabled=false")
class BackgroundJobRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private SQLQueryFactory queryFactory;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private BackgroundJobRepository repository;
    @Autowired
    private BackgroundActivityRepository activityRepository;

    @BeforeEach
    void clearData() {
        transactionTemplate.executeWithoutResult(status -> {
            queryFactory.delete(QAssetMetadata.assetMetadata).execute();
            queryFactory.delete(QAssetFiles.assetFiles).execute();
            queryFactory.delete(QAssets.assets).execute();
            queryFactory.delete(QLibraryRoots.libraryRoots).execute();
            queryFactory.delete(QLibraries.libraries).execute();
            queryFactory.delete(QUsers.users).execute();
            queryFactory.delete(QBackgroundJobs.backgroundJobs).execute();
        });
    }

    @Test
    void dedupeKeyReusesActiveJob() {
        OffsetDateTime now = OffsetDateTime.now();
        BackgroundJobCreate create = job("library-catalog-root", "library:1:root:1", "library:1");

        UUID firstId = transactionTemplate.execute(status -> repository.enqueue(create, now));
        UUID secondId = transactionTemplate.execute(status -> repository.enqueue(create, now));

        assertThat(secondId).isEqualTo(firstId);
        List<UUID> jobIds = transactionTemplate.execute(status ->
                queryFactory.select(QBackgroundJobs.backgroundJobs.id).from(QBackgroundJobs.backgroundJobs).fetch()
        );

        assertThat(jobIds)
                .containsExactly(firstId);
    }

    @Test
    void claimReadyJobsSkipsDuplicateConcurrencyKeysAndCanClaimAfterCompletion() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID firstId = transactionTemplate.execute(status -> repository.enqueue(
                job("library-catalog-root", "library:1:root:1", "library:1"), now));
        UUID secondId = transactionTemplate.execute(status -> repository.enqueue(
                job("library-catalog-subtree", "library:1:root:1:/new", "library:1"), now));

        List<BackgroundJobRecord> firstClaim = transactionTemplate.execute(status -> repository.claimReadyJobs(
                5,
                "worker-1",
                now.plusSeconds(1),
                now.plusMinutes(5)
        ));
        transactionTemplate.executeWithoutResult(status ->
                repository.complete(firstClaim.getFirst().id(), "worker-1", null, now.plusSeconds(2))
        );
        List<BackgroundJobRecord> secondClaim = transactionTemplate.execute(status -> repository.claimReadyJobs(
                5,
                "worker-2",
                now.plusSeconds(3),
                now.plusMinutes(5)
        ));

        assertThat(firstClaim).extracting(BackgroundJobRecord::id).containsExactly(firstId);
        assertThat(secondClaim).extracting(BackgroundJobRecord::id).containsExactly(secondId);
    }

    @Test
    void summarizesQueueCountsAndRecentProblemJobs() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID pendingId = transactionTemplate.execute(status -> repository.enqueue(
                job("asset-identity-backfill", "identity:1", "identity:1"), now));
        UUID deadLetterId = transactionTemplate.execute(status -> repository.enqueue(
                job("filesystem-change-event", "watcher:1", "watcher:1"), now));
        transactionTemplate.executeWithoutResult(status -> {
            List<BackgroundJobRecord> claimed = repository.claimReadyJobs(2, "worker-1", now.plusSeconds(1), now.plusMinutes(5));
            claimed.stream()
                    .filter(job -> job.id().equals(deadLetterId))
                    .findFirst()
                    .ifPresent(job -> repository.deadLetter(
                            job.id(),
                            "worker-1",
                            "watcher_overflow",
                            "Watcher overflow",
                            now.plusSeconds(2)
                    ));
        });

        List<BackgroundJobStatusSummary> summaries = transactionTemplate.execute(status ->
                repository.summarizeByTypeAndStatus());
        List<BackgroundJobProblemSummary> problems = transactionTemplate.execute(status ->
                repository.latestProblemJobs(10));

        assertThat(summaries)
                .anySatisfy(summary -> {
                    assertThat(summary.jobType()).isEqualTo("asset-identity-backfill");
                    assertThat(summary.status()).isEqualTo(BackgroundJobRepository.STATUS_RUNNING);
                    assertThat(summary.count()).isEqualTo(1);
                })
                .anySatisfy(summary -> {
                    assertThat(summary.jobType()).isEqualTo("filesystem-change-event");
                    assertThat(summary.status()).isEqualTo(BackgroundJobRepository.STATUS_DEAD_LETTER);
                    assertThat(summary.count()).isEqualTo(1);
                });
        assertThat(problems).extracting(BackgroundJobProblemSummary::id).contains(deadLetterId);
        assertThat(problems).extracting(BackgroundJobProblemSummary::id).doesNotContain(pendingId);
    }

    @Test
    void jobLifecycleSupportsFiltersRetryCancelAndDeadLetterQueries() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID includedId = transactionTemplate.execute(status -> repository.enqueue(
                job("included", "included:1", "included:group"), now));
        UUID excludedId = transactionTemplate.execute(status -> repository.enqueue(
                job("excluded", "excluded:1", "excluded:group"), now));

        List<BackgroundJobRecord> includedClaim = transactionTemplate.execute(status -> repository.claimReadyJobs(
                10,
                "worker-1",
                now.plusSeconds(1),
                now.plusMinutes(5),
                "included",
                null
        ));
        assertThat(includedClaim).extracting(BackgroundJobRecord::id).containsExactly(includedId);
        Boolean hasIncludedJob = transactionTemplate.execute(status ->
                repository.hasActiveJobs("included", "included:", null));
        Boolean hasIncludedJobExceptCurrent = transactionTemplate.execute(status ->
                repository.hasActiveJobs("included", "included:", includedId));
        assertThat(hasIncludedJob).isTrue();
        assertThat(hasIncludedJobExceptCurrent).isFalse();

        transactionTemplate.executeWithoutResult(status ->
                repository.heartbeat(includedId, "worker-1", now.plusMinutes(10), now.plusSeconds(2))
        );
        transactionTemplate.executeWithoutResult(status ->
                repository.retry(includedId, "worker-1", "temporary", "try again", now.plusSeconds(3), now.plusSeconds(2))
        );
        BackgroundJobRecord retried = transactionTemplate.execute(status -> repository.find(includedId)).orElseThrow();
        assertThat(retried.status()).isEqualTo(BackgroundJobRepository.STATUS_PENDING);
        assertThat(retried.lastErrorCode()).isEqualTo("temporary");

        List<BackgroundJobRecord> excludedClaim = transactionTemplate.execute(status -> repository.claimReadyJobs(
                10,
                "worker-2",
                now.plusSeconds(4),
                now.plusMinutes(5),
                null,
                "included"
        ));
        assertThat(excludedClaim).extracting(BackgroundJobRecord::id).containsExactly(excludedId);
        transactionTemplate.executeWithoutResult(status ->
                repository.cancel(excludedId, now.plusSeconds(5))
        );
        assertThat(transactionTemplate.execute(status -> repository.find(excludedId)).orElseThrow().status())
                .isEqualTo(BackgroundJobRepository.STATUS_CANCELLED);

        List<BackgroundJobRecord> retriedClaim = transactionTemplate.execute(status -> repository.claimReadyJobs(
                10,
                "worker-3",
                now.plusSeconds(5),
                now.plusMinutes(5)
        ));
        assertThat(retriedClaim).extracting(BackgroundJobRecord::id).containsExactly(includedId);
        transactionTemplate.executeWithoutResult(status ->
                repository.deadLetter(includedId, "worker-3", "terminal", "gave up", now.plusSeconds(6))
        );

        List<BackgroundJobRecord> deadLetters = transactionTemplate.execute(status -> repository.deadLetterJobs("included", 0));
        List<BackgroundJobRecord> latestJobs = transactionTemplate.execute(status -> repository.latestJobs(10));
        Optional<BackgroundJobRecord> blankDedupe = transactionTemplate.execute(status -> repository.findActiveByDedupeKey(" "));

        assertThat(deadLetters)
                .extracting(BackgroundJobRecord::id)
                .containsExactly(includedId);
        assertThat(latestJobs)
                .extracting(BackgroundJobRecord::id)
                .contains(includedId, excludedId);
        assertThat(blankDedupe).isEmpty();
    }

    @Test
    void fileActivityIncludesCompletedMetadataExtraction() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID assetId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> {
            queryFactory.insert(QFileActivityEvents.fileActivityEvents)
                    .set(QFileActivityEvents.fileActivityEvents.id, UUID.randomUUID())
                    .set(QFileActivityEvents.fileActivityEvents.path, "/photos/extracted.jpg")
                    .set(QFileActivityEvents.fileActivityEvents.status, "extracted")
                    .set(QFileActivityEvents.fileActivityEvents.occurredAt, now)
                    .execute();
        });

        BackgroundActivityRepository.PersistedFileActivityPage page = transactionTemplate.execute(status ->
                activityRepository.searchFileActivity(null, null, null, null, 0, 25));

        assertThat(page.items()).anySatisfy(item -> {
            assertThat(item.assetId()).isNull();
            assertThat(item.path()).isEqualTo("/photos/extracted.jpg");
            assertThat(item.result()).isEqualTo("extracted");
            assertThat(item.observedAt()).isEqualTo(now);
        });
    }

    private BackgroundJobCreate job(String jobType, String dedupeKey, String concurrencyKey) {
        return new BackgroundJobCreate(
                jobType,
                "{\"ok\":true}",
                0,
                3,
                OffsetDateTime.now().minusSeconds(1),
                concurrencyKey,
                dedupeKey
        );
    }
}
