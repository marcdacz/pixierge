package com.pixierge.api.background;

import com.pixierge.api.db.QBackgroundJobs;
import com.querydsl.core.QueryFlag;
import com.querydsl.core.Tuple;
import com.querydsl.sql.SQLQueryFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class BackgroundJobRepository {

    static final String STATUS_PENDING = "pending";
    static final String STATUS_RUNNING = "running";
    static final String STATUS_SUCCEEDED = "succeeded";
    static final String STATUS_FAILED = "failed";
    static final String STATUS_DEAD_LETTER = "dead_letter";
    static final String STATUS_CANCELLED = "cancelled";

    private static final QBackgroundJobs JOBS = QBackgroundJobs.backgroundJobs;
    private static final QBackgroundJobs ACTIVE_JOBS = new QBackgroundJobs("active_jobs");
    private static final String ON_CONFLICT_DO_NOTHING = " ON CONFLICT DO NOTHING";

    private final SQLQueryFactory queryFactory;

    public BackgroundJobRepository(SQLQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public UUID enqueue(BackgroundJobCreate create, OffsetDateTime now) {
        UUID id = UUID.randomUUID();
        long inserted = queryFactory.insert(JOBS)
                .set(JOBS.id, id)
                .set(JOBS.jobType, create.jobType())
                .set(JOBS.payloadJson, create.payloadJson())
                .set(JOBS.status, STATUS_PENDING)
                .set(JOBS.priority, create.priority())
                .set(JOBS.attempts, 0)
                .set(JOBS.maxAttempts, create.maxAttempts())
                .set(JOBS.nextRunAt, create.nextRunAt() == null ? now : create.nextRunAt())
                .set(JOBS.concurrencyKey, create.concurrencyKey())
                .set(JOBS.dedupeKey, create.dedupeKey())
                .set(JOBS.createdAt, now)
                .set(JOBS.updatedAt, now)
                .addFlag(QueryFlag.Position.END, ON_CONFLICT_DO_NOTHING)
                .execute();
        if (inserted == 1) {
            return id;
        }
        return findActiveByDedupeKey(create.dedupeKey())
                .map(BackgroundJobRecord::id)
                .orElse(id);
    }

    @Transactional
    public List<BackgroundJobRecord> claimReadyJobs(
            int limit,
            String workerId,
            OffsetDateTime now,
            OffsetDateTime leaseUntil
    ) {
        List<Tuple> rows = selectJobs()
                .where(JOBS.attempts.lt(JOBS.maxAttempts)
                        .and(JOBS.status.eq(STATUS_PENDING)
                                .and(JOBS.nextRunAt.loe(now))
                                .or(JOBS.status.eq(STATUS_RUNNING)
                                        .and(JOBS.leaseUntil.isNotNull())
                                        .and(JOBS.leaseUntil.loe(now)))))
                .orderBy(JOBS.priority.desc(), JOBS.nextRunAt.asc(), JOBS.createdAt.asc())
                .limit(Math.max(1, limit) * 4L)
                .addFlag(QueryFlag.Position.END, " FOR UPDATE SKIP LOCKED")
                .fetch();
        rows = keepClaimableConcurrencyKeys(rows, limit, now);

        for (Tuple row : rows) {
            UUID id = row.get(JOBS.id);
            Integer attempts = row.get(JOBS.attempts);
            queryFactory.update(JOBS)
                    .set(JOBS.status, STATUS_RUNNING)
                    .set(JOBS.attempts, attempts == null ? 1 : attempts + 1)
                    .set(JOBS.lockedBy, workerId)
                    .set(JOBS.leaseUntil, leaseUntil)
                    .set(JOBS.updatedAt, now)
                    .where(JOBS.id.eq(id))
                    .execute();
        }

        return rows.stream()
                .map(row -> toRecord(
                        row,
                        STATUS_RUNNING,
                        workerId,
                        leaseUntil,
                        (row.get(JOBS.attempts) == null ? 0 : row.get(JOBS.attempts)) + 1,
                        now
                ))
                .toList();
    }

    private List<Tuple> keepClaimableConcurrencyKeys(List<Tuple> rows, int limit, OffsetDateTime now) {
        Set<String> claimedKeys = new HashSet<>();
        return rows.stream()
                .filter(row -> {
                    String concurrencyKey = row.get(JOBS.concurrencyKey);
                    UUID jobId = row.get(JOBS.id);
                    if (concurrencyKey == null || !claimedKeys.add(concurrencyKey)) {
                        return false;
                    }
                    return !hasActiveRunningJobForKey(concurrencyKey, jobId, now);
                })
                .limit(Math.max(1, limit))
                .toList();
    }

    private boolean hasActiveRunningJobForKey(String concurrencyKey, UUID ignoredJobId, OffsetDateTime now) {
        return queryFactory
                .select(ACTIVE_JOBS.id)
                .from(ACTIVE_JOBS)
                .where(ACTIVE_JOBS.concurrencyKey.eq(concurrencyKey)
                        .and(ACTIVE_JOBS.status.eq(STATUS_RUNNING))
                        .and(ACTIVE_JOBS.leaseUntil.isNotNull())
                        .and(ACTIVE_JOBS.leaseUntil.gt(now))
                        .and(ACTIVE_JOBS.id.ne(ignoredJobId)))
                .fetchFirst() != null;
    }

    public void heartbeat(UUID jobId, String workerId, OffsetDateTime leaseUntil, OffsetDateTime now) {
        queryFactory.update(JOBS)
                .set(JOBS.leaseUntil, leaseUntil)
                .set(JOBS.updatedAt, now)
                .where(JOBS.id.eq(jobId)
                        .and(JOBS.status.eq(STATUS_RUNNING))
                        .and(JOBS.lockedBy.eq(workerId)))
                .execute();
    }

    public void complete(UUID jobId, String workerId, String progressJson, OffsetDateTime now) {
        queryFactory.update(JOBS)
                .set(JOBS.status, STATUS_SUCCEEDED)
                .setNull(JOBS.leaseUntil)
                .setNull(JOBS.lockedBy)
                .set(JOBS.progressJson, progressJson)
                .set(JOBS.completedAt, now)
                .set(JOBS.updatedAt, now)
                .where(JOBS.id.eq(jobId)
                        .and(JOBS.status.eq(STATUS_RUNNING))
                        .and(JOBS.lockedBy.eq(workerId)))
                .execute();
    }

    public void retry(UUID jobId, String workerId, String errorCode, String errorMessage, OffsetDateTime nextRunAt, OffsetDateTime now) {
        queryFactory.update(JOBS)
                .set(JOBS.status, STATUS_PENDING)
                .setNull(JOBS.leaseUntil)
                .setNull(JOBS.lockedBy)
                .set(JOBS.nextRunAt, nextRunAt)
                .set(JOBS.lastErrorCode, errorCode)
                .set(JOBS.lastErrorMessage, errorMessage)
                .set(JOBS.updatedAt, now)
                .where(JOBS.id.eq(jobId)
                        .and(JOBS.status.eq(STATUS_RUNNING))
                        .and(JOBS.lockedBy.eq(workerId)))
                .execute();
    }

    public void deadLetter(UUID jobId, String workerId, String errorCode, String errorMessage, OffsetDateTime now) {
        queryFactory.update(JOBS)
                .set(JOBS.status, STATUS_DEAD_LETTER)
                .setNull(JOBS.leaseUntil)
                .setNull(JOBS.lockedBy)
                .set(JOBS.lastErrorCode, errorCode)
                .set(JOBS.lastErrorMessage, errorMessage)
                .set(JOBS.completedAt, now)
                .set(JOBS.updatedAt, now)
                .where(JOBS.id.eq(jobId)
                        .and(JOBS.status.eq(STATUS_RUNNING))
                        .and(JOBS.lockedBy.eq(workerId)))
                .execute();
    }

    public void cancel(UUID jobId, OffsetDateTime now) {
        queryFactory.update(JOBS)
                .set(JOBS.status, STATUS_CANCELLED)
                .setNull(JOBS.leaseUntil)
                .setNull(JOBS.lockedBy)
                .set(JOBS.completedAt, now)
                .set(JOBS.updatedAt, now)
                .where(JOBS.id.eq(jobId)
                        .and(JOBS.status.in(STATUS_PENDING, STATUS_RUNNING)))
                .execute();
    }

    @Transactional(readOnly = true)
    public Optional<BackgroundJobRecord> find(UUID jobId) {
        Tuple row = selectJobs()
                .where(JOBS.id.eq(jobId))
                .fetchOne();
        return Optional.ofNullable(row).map(this::toRecord);
    }

    @Transactional(readOnly = true)
    public Optional<BackgroundJobRecord> findActiveByDedupeKey(String dedupeKey) {
        if (dedupeKey == null || dedupeKey.isBlank()) {
            return Optional.empty();
        }
        Tuple row = selectJobs()
                .where(JOBS.dedupeKey.eq(dedupeKey)
                        .and(JOBS.status.in(STATUS_PENDING, STATUS_RUNNING)))
                .fetchFirst();
        return Optional.ofNullable(row).map(this::toRecord);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveJobs(String jobType, String dedupeKeyPrefix, UUID excludedJobId) {
        var predicate = JOBS.jobType.eq(jobType)
                .and(JOBS.status.in(STATUS_PENDING, STATUS_RUNNING));
        if (dedupeKeyPrefix != null && !dedupeKeyPrefix.isBlank()) {
            predicate = predicate.and(JOBS.dedupeKey.startsWith(dedupeKeyPrefix));
        }
        if (excludedJobId != null) {
            predicate = predicate.and(JOBS.id.ne(excludedJobId));
        }
        Integer exists = queryFactory.selectOne()
                .from(JOBS)
                .where(predicate)
                .fetchFirst();
        return exists != null;
    }

    private com.querydsl.sql.SQLQuery<Tuple> selectJobs() {
        return queryFactory
                .select(
                        JOBS.id,
                        JOBS.jobType,
                        JOBS.payloadJson,
                        JOBS.status,
                        JOBS.priority,
                        JOBS.attempts,
                        JOBS.maxAttempts,
                        JOBS.nextRunAt,
                        JOBS.leaseUntil,
                        JOBS.lockedBy,
                        JOBS.concurrencyKey,
                        JOBS.dedupeKey,
                        JOBS.progressJson,
                        JOBS.lastErrorCode,
                        JOBS.lastErrorMessage,
                        JOBS.createdAt,
                        JOBS.updatedAt,
                        JOBS.completedAt
                )
                .from(JOBS);
    }

    private BackgroundJobRecord toRecord(Tuple row) {
        return toRecord(
                row,
                row.get(JOBS.status),
                row.get(JOBS.lockedBy),
                row.get(JOBS.leaseUntil),
                row.get(JOBS.attempts) == null ? 0 : row.get(JOBS.attempts),
                row.get(JOBS.updatedAt)
        );
    }

    private BackgroundJobRecord toRecord(
            Tuple row,
            String status,
            String lockedBy,
            OffsetDateTime leaseUntil,
            int attempts,
            OffsetDateTime updatedAt
    ) {
        return new BackgroundJobRecord(
                row.get(JOBS.id),
                row.get(JOBS.jobType),
                row.get(JOBS.payloadJson),
                status,
                row.get(JOBS.priority) == null ? 0 : row.get(JOBS.priority),
                attempts,
                row.get(JOBS.maxAttempts) == null ? 1 : row.get(JOBS.maxAttempts),
                row.get(JOBS.nextRunAt),
                leaseUntil,
                lockedBy,
                row.get(JOBS.concurrencyKey),
                row.get(JOBS.dedupeKey),
                row.get(JOBS.progressJson),
                row.get(JOBS.lastErrorCode),
                row.get(JOBS.lastErrorMessage),
                row.get(JOBS.createdAt),
                updatedAt,
                row.get(JOBS.completedAt)
        );
    }
}
