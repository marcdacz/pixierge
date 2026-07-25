package com.pixierge.api.background;

import com.pixierge.api.db.QBackgroundJobs;
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
    void fileActivityIncludesCompletedMetadataExtraction() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID userId = UUID.randomUUID();
        UUID libraryId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> {
            queryFactory.insert(QUsers.users)
                    .set(QUsers.users.id, userId)
                    .set(QUsers.users.username, "background-activity-owner")
                    .set(QUsers.users.status, "active")
                    .set(QUsers.users.createdAt, now)
                    .set(QUsers.users.updatedAt, now)
                    .execute();
            queryFactory.insert(QLibraries.libraries)
                    .set(QLibraries.libraries.id, libraryId)
                    .set(QLibraries.libraries.name, "Background activity")
                    .set(QLibraries.libraries.status, "active")
                    .set(QLibraries.libraries.createdBy, userId)
                    .set(QLibraries.libraries.createdAt, now)
                    .set(QLibraries.libraries.updatedAt, now)
                    .execute();
            queryFactory.insert(QLibraryRoots.libraryRoots)
                    .set(QLibraryRoots.libraryRoots.id, rootId)
                    .set(QLibraryRoots.libraryRoots.libraryId, libraryId)
                    .set(QLibraryRoots.libraryRoots.path, "/photos")
                    .set(QLibraryRoots.libraryRoots.normalizedPath, "/photos")
                    .set(QLibraryRoots.libraryRoots.createdAt, now)
                    .set(QLibraryRoots.libraryRoots.updatedAt, now)
                    .execute();
            queryFactory.insert(QAssets.assets)
                    .set(QAssets.assets.id, assetId)
                    .set(QAssets.assets.contentHash, "metadata-activity-hash")
                    .set(QAssets.assets.mediaType, "image/jpeg")
                    .set(QAssets.assets.availableFileCount, 1)
                    .set(QAssets.assets.firstObservedAt, now)
                    .set(QAssets.assets.lastObservedAt, now)
                    .execute();
            queryFactory.insert(QAssetFiles.assetFiles)
                    .set(QAssetFiles.assetFiles.id, UUID.randomUUID())
                    .set(QAssetFiles.assetFiles.assetId, assetId)
                    .set(QAssetFiles.assetFiles.libraryId, libraryId)
                    .set(QAssetFiles.assetFiles.rootId, rootId)
                    .set(QAssetFiles.assetFiles.path, "/photos/extracted.jpg")
                    .set(QAssetFiles.assetFiles.normalizedPath, "/photos/extracted.jpg")
                    .set(QAssetFiles.assetFiles.fileName, "extracted.jpg")
                    .set(QAssetFiles.assetFiles.sizeBytes, 100L)
                    .set(QAssetFiles.assetFiles.modifiedAt, now)
                    .set(QAssetFiles.assetFiles.contentHash, "metadata-activity-hash")
                    .set(QAssetFiles.assetFiles.status, "active")
                    .set(QAssetFiles.assetFiles.firstObservedAt, now)
                    .set(QAssetFiles.assetFiles.lastObservedAt, now)
                    .execute();
            queryFactory.insert(QAssetMetadata.assetMetadata)
                    .set(QAssetMetadata.assetMetadata.assetId, assetId)
                    .set(QAssetMetadata.assetMetadata.sourceVersion, "test")
                    .set(QAssetMetadata.assetMetadata.extractionStatus, "extracted")
                    .set(QAssetMetadata.assetMetadata.metadataStatus, "extracted")
                    .set(QAssetMetadata.assetMetadata.metadataExtractedAt, now)
                    .execute();
        });

        BackgroundActivityRepository.PersistedFileActivityPage page = transactionTemplate.execute(status ->
                activityRepository.searchFileActivity(null, null, null, null, 0, 25));

        assertThat(page.items()).anySatisfy(item -> {
            assertThat(item.assetId()).isEqualTo(assetId);
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
