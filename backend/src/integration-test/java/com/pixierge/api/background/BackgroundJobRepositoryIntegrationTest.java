package com.pixierge.api.background;

import com.pixierge.api.db.QBackgroundJobs;
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

    @BeforeEach
    void clearData() {
        transactionTemplate.executeWithoutResult(status ->
                queryFactory.delete(QBackgroundJobs.backgroundJobs).execute()
        );
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
