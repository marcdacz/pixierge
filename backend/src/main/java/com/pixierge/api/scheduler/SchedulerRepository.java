package com.pixierge.api.scheduler;

import com.pixierge.api.db.QScheduledJobLocks;
import com.pixierge.api.db.QScheduledJobRuns;
import com.pixierge.api.db.QScheduledJobs;
import com.querydsl.core.QueryFlag;
import com.querydsl.core.Tuple;
import com.querydsl.sql.SQLQueryFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SchedulerRepository {

    private static final QScheduledJobs JOBS = QScheduledJobs.scheduledJobs;
    private static final QScheduledJobRuns RUNS = QScheduledJobRuns.scheduledJobRuns;
    private static final QScheduledJobLocks LOCKS = QScheduledJobLocks.scheduledJobLocks;
    private static final String ON_LOCK_CONFLICT_DO_NOTHING = " on conflict (concurrency_key) do nothing";

    private final SQLQueryFactory queryFactory;

    public SchedulerRepository(SQLQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Transactional(readOnly = true)
    public List<SchedulerJobRecord> listJobs() {
        return queryFactory
                .select(
                        JOBS.id,
                        JOBS.jobKey,
                        JOBS.displayName,
                        JOBS.description,
                        JOBS.ownerType,
                        JOBS.enabled,
                        JOBS.cronExpression,
                        JOBS.timezone,
                        JOBS.nextRunAt,
                        JOBS.lastRunAt,
                        JOBS.lastStatus,
                        JOBS.timeoutSeconds,
                        JOBS.concurrencyKey,
                        JOBS.createdAt,
                        JOBS.updatedAt
                )
                .from(JOBS)
                .orderBy(JOBS.displayName.asc())
                .fetch()
                .stream()
                .map(this::toJob)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<SchedulerJobRecord> findJob(UUID jobId) {
        Tuple row = queryFactory
                .select(
                        JOBS.id,
                        JOBS.jobKey,
                        JOBS.displayName,
                        JOBS.description,
                        JOBS.ownerType,
                        JOBS.enabled,
                        JOBS.cronExpression,
                        JOBS.timezone,
                        JOBS.nextRunAt,
                        JOBS.lastRunAt,
                        JOBS.lastStatus,
                        JOBS.timeoutSeconds,
                        JOBS.concurrencyKey,
                        JOBS.createdAt,
                        JOBS.updatedAt
                )
                .from(JOBS)
                .where(JOBS.id.eq(jobId))
                .fetchOne();
        return Optional.ofNullable(row).map(this::toJob);
    }

    @Transactional(readOnly = true)
    public Optional<SchedulerJobRecord> findJobByKey(String jobKey) {
        Tuple row = queryFactory
                .select(
                        JOBS.id,
                        JOBS.jobKey,
                        JOBS.displayName,
                        JOBS.description,
                        JOBS.ownerType,
                        JOBS.enabled,
                        JOBS.cronExpression,
                        JOBS.timezone,
                        JOBS.nextRunAt,
                        JOBS.lastRunAt,
                        JOBS.lastStatus,
                        JOBS.timeoutSeconds,
                        JOBS.concurrencyKey,
                        JOBS.createdAt,
                        JOBS.updatedAt
                )
                .from(JOBS)
                .where(JOBS.jobKey.eq(jobKey))
                .fetchOne();
        return Optional.ofNullable(row).map(this::toJob);
    }

    @Transactional(readOnly = true)
    public List<SchedulerJobRecord> findDueJobs(OffsetDateTime now) {
        return queryFactory
                .select(
                        JOBS.id,
                        JOBS.jobKey,
                        JOBS.displayName,
                        JOBS.description,
                        JOBS.ownerType,
                        JOBS.enabled,
                        JOBS.cronExpression,
                        JOBS.timezone,
                        JOBS.nextRunAt,
                        JOBS.lastRunAt,
                        JOBS.lastStatus,
                        JOBS.timeoutSeconds,
                        JOBS.concurrencyKey,
                        JOBS.createdAt,
                        JOBS.updatedAt
                )
                .from(JOBS)
                .where(JOBS.enabled.isTrue()
                        .and(JOBS.nextRunAt.isNotNull())
                        .and(JOBS.nextRunAt.loe(now)))
                .orderBy(JOBS.nextRunAt.asc())
                .fetch()
                .stream()
                .map(this::toJob)
                .toList();
    }

    public UUID insertJob(
            String jobKey,
            String displayName,
            String description,
            String ownerType,
            boolean enabled,
            String cronExpression,
            String timezone,
            OffsetDateTime nextRunAt,
            int timeoutSeconds,
            String concurrencyKey
    ) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        queryFactory.insert(JOBS)
                .set(JOBS.id, id)
                .set(JOBS.jobKey, jobKey)
                .set(JOBS.displayName, displayName)
                .set(JOBS.description, description)
                .set(JOBS.ownerType, ownerType)
                .set(JOBS.enabled, enabled)
                .set(JOBS.cronExpression, cronExpression)
                .set(JOBS.timezone, timezone)
                .set(JOBS.nextRunAt, nextRunAt)
                .set(JOBS.timeoutSeconds, timeoutSeconds)
                .set(JOBS.concurrencyKey, concurrencyKey)
                .set(JOBS.createdAt, now)
                .set(JOBS.updatedAt, now)
                .execute();
        return id;
    }

    public void updateJobDefinitionMetadata(
            UUID jobId,
            String displayName,
            String description,
            int timeoutSeconds,
            String concurrencyKey
    ) {
        queryFactory.update(JOBS)
                .set(JOBS.displayName, displayName)
                .set(JOBS.description, description)
                .set(JOBS.timeoutSeconds, timeoutSeconds)
                .set(JOBS.concurrencyKey, concurrencyKey)
                .set(JOBS.updatedAt, OffsetDateTime.now())
                .where(JOBS.id.eq(jobId))
                .execute();
    }

    public void updateJobSchedule(
            UUID jobId,
            boolean enabled,
            String cronExpression,
            String timezone,
            OffsetDateTime nextRunAt
    ) {
        queryFactory.update(JOBS)
                .set(JOBS.enabled, enabled)
                .set(JOBS.cronExpression, cronExpression)
                .set(JOBS.timezone, timezone)
                .set(JOBS.nextRunAt, nextRunAt)
                .set(JOBS.updatedAt, OffsetDateTime.now())
                .where(JOBS.id.eq(jobId))
                .execute();
    }

    public void updateJobRunState(
            UUID jobId,
            OffsetDateTime lastRunAt,
            String lastStatus
    ) {
        queryFactory.update(JOBS)
                .set(JOBS.lastRunAt, lastRunAt)
                .set(JOBS.lastStatus, lastStatus)
                .set(JOBS.updatedAt, OffsetDateTime.now())
                .where(JOBS.id.eq(jobId))
                .execute();
    }

    public boolean advanceNextRunIfScheduleUnchanged(
            SchedulerJobRecord expectedJob,
            OffsetDateTime nextRunAt
    ) {
        long updated = queryFactory.update(JOBS)
                .set(JOBS.nextRunAt, nextRunAt)
                .set(JOBS.updatedAt, OffsetDateTime.now())
                .where(JOBS.id.eq(expectedJob.id())
                        .and(JOBS.enabled.eq(expectedJob.enabled()))
                        .and(JOBS.cronExpression.eq(expectedJob.cronExpression()))
                        .and(JOBS.timezone.eq(expectedJob.timezone()))
                        .and(JOBS.nextRunAt.eq(expectedJob.nextRunAt())))
                .execute();
        return updated == 1;
    }

    public UUID insertRun(UUID jobId, String triggerSource, String status, OffsetDateTime startedAt) {
        UUID id = UUID.randomUUID();
        queryFactory.insert(RUNS)
                .set(RUNS.id, id)
                .set(RUNS.jobId, jobId)
                .set(RUNS.triggerSource, triggerSource)
                .set(RUNS.status, status)
                .set(RUNS.startedAt, startedAt)
                .set(RUNS.createdAt, startedAt)
                .execute();
        return id;
    }

    public void completeRun(
            UUID runId,
            String status,
            OffsetDateTime finishedAt,
            long durationMs,
            String summaryJson,
            String errorMessage
    ) {
        queryFactory.update(RUNS)
                .set(RUNS.status, status)
                .set(RUNS.finishedAt, finishedAt)
                .set(RUNS.durationMs, durationMs)
                .set(RUNS.summaryJson, summaryJson)
                .set(RUNS.errorMessage, errorMessage)
                .where(RUNS.id.eq(runId))
                .execute();
    }

    @Transactional(readOnly = true)
    public Optional<SchedulerJobRunRecord> findRun(UUID runId) {
        Tuple row = queryFactory
                .select(
                        RUNS.id,
                        RUNS.jobId,
                        RUNS.triggerSource,
                        RUNS.status,
                        RUNS.startedAt,
                        RUNS.finishedAt,
                        RUNS.durationMs,
                        RUNS.summaryJson,
                        RUNS.errorMessage,
                        RUNS.createdAt
                )
                .from(RUNS)
                .where(RUNS.id.eq(runId))
                .fetchOne();
        return Optional.ofNullable(row).map(this::toRun);
    }

    @Transactional(readOnly = true)
    public List<SchedulerJobRunRecord> listRuns(UUID jobId, int limit) {
        return queryFactory
                .select(
                        RUNS.id,
                        RUNS.jobId,
                        RUNS.triggerSource,
                        RUNS.status,
                        RUNS.startedAt,
                        RUNS.finishedAt,
                        RUNS.durationMs,
                        RUNS.summaryJson,
                        RUNS.errorMessage,
                        RUNS.createdAt
                )
                .from(RUNS)
                .where(RUNS.jobId.eq(jobId))
                .orderBy(RUNS.startedAt.desc())
                .limit(limit)
                .fetch()
                .stream()
                .map(this::toRun)
                .toList();
    }

    public boolean tryAcquireLock(
            String concurrencyKey,
            UUID jobId,
            UUID runId,
            OffsetDateTime acquiredAt,
            OffsetDateTime staleBefore
    ) {
        queryFactory.delete(LOCKS)
                .where(LOCKS.concurrencyKey.eq(concurrencyKey)
                        .and(LOCKS.acquiredAt.loe(staleBefore)))
                .execute();
        long inserted = queryFactory.insert(LOCKS)
                .set(LOCKS.concurrencyKey, concurrencyKey)
                .set(LOCKS.jobId, jobId)
                .set(LOCKS.runId, runId)
                .set(LOCKS.acquiredAt, acquiredAt)
                .addFlag(QueryFlag.Position.END, ON_LOCK_CONFLICT_DO_NOTHING)
                .execute();
        return inserted == 1;
    }

    public void releaseLock(String concurrencyKey, UUID runId) {
        queryFactory.delete(LOCKS)
                .where(LOCKS.concurrencyKey.eq(concurrencyKey)
                        .and(LOCKS.runId.eq(runId)))
                .execute();
    }

    @Transactional(readOnly = true)
    public boolean isLocked(String concurrencyKey) {
        return queryFactory
                .select(LOCKS.concurrencyKey)
                .from(LOCKS)
                .where(LOCKS.concurrencyKey.eq(concurrencyKey))
                .fetchFirst() != null;
    }

    private SchedulerJobRecord toJob(Tuple row) {
        return new SchedulerJobRecord(
                row.get(JOBS.id),
                row.get(JOBS.jobKey),
                row.get(JOBS.displayName),
                row.get(JOBS.description),
                row.get(JOBS.ownerType),
                Boolean.TRUE.equals(row.get(JOBS.enabled)),
                row.get(JOBS.cronExpression),
                row.get(JOBS.timezone),
                row.get(JOBS.nextRunAt),
                row.get(JOBS.lastRunAt),
                row.get(JOBS.lastStatus),
                row.get(JOBS.timeoutSeconds),
                row.get(JOBS.concurrencyKey),
                row.get(JOBS.createdAt),
                row.get(JOBS.updatedAt)
        );
    }

    private SchedulerJobRunRecord toRun(Tuple row) {
        return new SchedulerJobRunRecord(
                row.get(RUNS.id),
                row.get(RUNS.jobId),
                row.get(RUNS.triggerSource),
                row.get(RUNS.status),
                row.get(RUNS.startedAt),
                row.get(RUNS.finishedAt),
                row.get(RUNS.durationMs),
                row.get(RUNS.summaryJson),
                row.get(RUNS.errorMessage),
                row.get(RUNS.createdAt)
        );
    }
}
