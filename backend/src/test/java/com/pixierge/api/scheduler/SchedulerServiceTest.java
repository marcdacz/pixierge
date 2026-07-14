package com.pixierge.api.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchedulerServiceTest {

    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-13T00:00:00Z");

    @Test
    void updateJobTrimsScheduleFieldsAndClearsNextRunWhenDisabled() {
        FakeSchedulerRepository repository = new FakeSchedulerRepository();
        repository.job = job(true, "0 0 * * * *", "UTC", NOW.plusHours(1), null, null);
        SchedulerService service = service(repository, successfulRegistry(), new SyncTaskExecutor());

        SchedulerJobResponse response = service.updateJob(
                JOB_ID,
                new UpdateSchedulerJobRequest(false, " 0 30 * * * * ", " Australia/Melbourne ")
        );

        assertThat(response.enabled()).isFalse();
        assertThat(repository.updatedEnabled).isFalse();
        assertThat(repository.updatedCronExpression).isEqualTo("0 30 * * * *");
        assertThat(repository.updatedTimezone).isEqualTo("Australia/Melbourne");
        assertThat(repository.updatedNextRunAt).isNull();
    }

    @Test
    void updateJobRejectsInvalidCronBeforePersistingChanges() {
        FakeSchedulerRepository repository = new FakeSchedulerRepository();
        repository.job = job(true, "0 0 * * * *", "UTC", NOW.plusHours(1), null, null);
        SchedulerService service = service(repository, successfulRegistry(), new SyncTaskExecutor());

        assertThatThrownBy(() -> service.updateJob(
                JOB_ID,
                new UpdateSchedulerJobRequest(true, "not a cron", "UTC")
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(repository.updatedCronExpression).isNull();
    }

    @Test
    void runNowCompletesSuccessfulHandlerAndReleasesTheLock() {
        FakeSchedulerRepository repository = new FakeSchedulerRepository();
        repository.job = job(true, "0 0 * * * *", "UTC", NOW.plusHours(1), null, null);
        SchedulerService service = service(repository, successfulRegistry(), new SyncTaskExecutor());

        SchedulerJobRunResponse response = service.runNow(JOB_ID);

        assertThat(response.id()).isEqualTo(RUN_ID);
        assertThat(response.triggerSource()).isEqualTo("manual");
        assertThat(response.status()).isEqualTo("succeeded");
        assertThat(response.summaryJson()).isEqualTo("{\"ok\":true}");
        assertThat(repository.lockReleases).containsExactly("core.library-scan:" + RUN_ID);
        assertThat(repository.lastJobStatus).isEqualTo("succeeded");
    }

    @Test
    void runNowSkipsWhenConcurrencyLockCannotBeAcquired() {
        FakeSchedulerRepository repository = new FakeSchedulerRepository();
        repository.job = job(true, "0 0 * * * *", "UTC", NOW.plusHours(1), null, null);
        repository.acquireLock = false;
        CountingHandler handler = new CountingHandler(new SchedulerJobResult("{\"ok\":true}"));
        SchedulerService service = service(repository, registry(handler), new SyncTaskExecutor());

        assertThatThrownBy(() -> service.runNow(JOB_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(handler.invocations).isZero();
        assertThat(repository.run.status()).isEqualTo("skipped");
        assertThat(repository.run.errorMessage()).contains("Another run is already in progress");
        assertThat(repository.lockReleases).isEmpty();
    }

    @Test
    void runNowMarksRunFailedAndReleasesLockWhenExecutorRejectsTheTask() {
        FakeSchedulerRepository repository = new FakeSchedulerRepository();
        repository.job = job(true, "0 0 * * * *", "UTC", NOW.plusHours(1), null, null);
        RuntimeException rejection = new RuntimeException("executor unavailable");
        SchedulerService service = service(repository, successfulRegistry(), task -> {
            throw rejection;
        });

        assertThatThrownBy(() -> service.runNow(JOB_ID)).isSameAs(rejection);

        assertThat(repository.run.status()).isEqualTo("failed");
        assertThat(repository.run.errorMessage()).isEqualTo("executor unavailable");
        assertThat(repository.lockReleases).containsExactly("core.library-scan:" + RUN_ID);
        assertThat(repository.lastJobStatus).isEqualTo("failed");
    }

    @Test
    void pollDueJobsMarksOverlappingScheduledRunsAsSkippedAndAdvancesSchedule() {
        FakeSchedulerRepository repository = new FakeSchedulerRepository();
        repository.job = job(true, "0 0 * * * *", "UTC", NOW.minusHours(1), NOW.minusDays(1), "succeeded");
        repository.dueJobs = List.of(repository.job);
        repository.acquireLock = false;
        SchedulerService service = service(repository, successfulRegistry(), new SyncTaskExecutor());

        service.pollDueJobs();

        assertThat(repository.run.status()).isEqualTo("skipped");
        assertThat(repository.lastJobStatus).isEqualTo("skipped");
        assertThat(repository.lastRunAt).isEqualTo(NOW.minusDays(1));
        assertThat(repository.advancedJobs).containsExactly(JOB_ID);
        assertThat(repository.advancedNextRunAt).isAfter(NOW.minusHours(1));
    }

    @Test
    void executeRunRecordsHandlerFailureAndStillReleasesTheLock() {
        FakeSchedulerRepository repository = new FakeSchedulerRepository();
        repository.job = job(true, "0 0 * * * *", "UTC", NOW.plusHours(1), null, null);
        SchedulerService service = service(repository, registry(job -> {
            throw new IllegalStateException("metadata scan failed");
        }), new SyncTaskExecutor());

        SchedulerJobRunResponse response = service.runNow(JOB_ID);

        assertThat(response.status()).isEqualTo("failed");
        assertThat(response.errorMessage()).isEqualTo("metadata scan failed");
        assertThat(repository.lockReleases).containsExactly("core.library-scan:" + RUN_ID);
        assertThat(repository.lastJobStatus).isEqualTo("failed");
    }

    private SchedulerService service(
            FakeSchedulerRepository repository,
            SchedulerJobRegistry registry,
            TaskExecutor taskExecutor
    ) {
        return new SchedulerService(repository, registry, taskExecutor, new ImmediateTransactionTemplate());
    }

    private SchedulerJobRegistry successfulRegistry() {
        return registry(new CountingHandler(new SchedulerJobResult("{\"ok\":true}")));
    }

    private SchedulerJobRegistry registry(SchedulerJobHandler handler) {
        SchedulerJobDefinition definition = new SchedulerJobDefinition(
                "core.library-scan",
                "Library scan",
                "Scan libraries",
                "0 0 * * * *",
                "UTC",
                true,
                300,
                "core.library-scan",
                handler
        );
        return new SchedulerJobRegistry(List.of(definition));
    }

    private SchedulerJobRecord job(
            boolean enabled,
            String cronExpression,
            String timezone,
            OffsetDateTime nextRunAt,
            OffsetDateTime lastRunAt,
            String lastStatus
    ) {
        return new SchedulerJobRecord(
                JOB_ID,
                "core.library-scan",
                "Library scan",
                "Scan libraries",
                "core",
                enabled,
                cronExpression,
                timezone,
                nextRunAt,
                lastRunAt,
                lastStatus,
                300,
                "core.library-scan",
                NOW.minusDays(7),
                NOW.minusDays(1)
        );
    }

    private static final class CountingHandler implements SchedulerJobHandler {

        private final SchedulerJobResult result;
        private int invocations;

        private CountingHandler(SchedulerJobResult result) {
            this.result = result;
        }

        @Override
        public SchedulerJobResult execute(SchedulerJobRecord job) {
            invocations++;
            return result;
        }
    }

    private static final class FakeSchedulerRepository extends SchedulerRepository {

        private SchedulerJobRecord job;
        private SchedulerJobRunRecord run;
        private List<SchedulerJobRecord> dueJobs = List.of();
        private boolean acquireLock = true;
        private Boolean updatedEnabled;
        private String updatedCronExpression;
        private String updatedTimezone;
        private OffsetDateTime updatedNextRunAt;
        private OffsetDateTime lastRunAt;
        private String lastJobStatus;
        private OffsetDateTime advancedNextRunAt;
        private final List<String> lockReleases = new ArrayList<>();
        private final List<UUID> advancedJobs = new ArrayList<>();

        private FakeSchedulerRepository() {
            super(null);
        }

        @Override
        public List<SchedulerJobRecord> listJobs() {
            return job == null ? List.of() : List.of(job);
        }

        @Override
        public Optional<SchedulerJobRecord> findJob(UUID jobId) {
            return job != null && job.id().equals(jobId) ? Optional.of(job) : Optional.empty();
        }

        @Override
        public List<SchedulerJobRecord> findDueJobs(OffsetDateTime now) {
            return dueJobs;
        }

        @Override
        public void updateJobSchedule(
                UUID jobId,
                boolean enabled,
                String cronExpression,
                String timezone,
                OffsetDateTime nextRunAt
        ) {
            updatedEnabled = enabled;
            updatedCronExpression = cronExpression;
            updatedTimezone = timezone;
            updatedNextRunAt = nextRunAt;
            job = new SchedulerJobRecord(
                    job.id(),
                    job.jobKey(),
                    job.displayName(),
                    job.description(),
                    job.ownerType(),
                    enabled,
                    cronExpression,
                    timezone,
                    nextRunAt,
                    job.lastRunAt(),
                    job.lastStatus(),
                    job.timeoutSeconds(),
                    job.concurrencyKey(),
                    job.createdAt(),
                    OffsetDateTime.now()
            );
        }

        @Override
        public void updateJobRunState(UUID jobId, OffsetDateTime lastRunAt, String lastStatus) {
            this.lastRunAt = lastRunAt;
            this.lastJobStatus = lastStatus;
        }

        @Override
        public boolean advanceNextRunIfScheduleUnchanged(SchedulerJobRecord expectedJob, OffsetDateTime nextRunAt) {
            advancedJobs.add(expectedJob.id());
            advancedNextRunAt = nextRunAt;
            return true;
        }

        @Override
        public UUID insertRun(UUID jobId, String triggerSource, String status, OffsetDateTime startedAt) {
            run = new SchedulerJobRunRecord(
                    RUN_ID,
                    jobId,
                    triggerSource,
                    status,
                    startedAt,
                    null,
                    null,
                    null,
                    null,
                    startedAt
            );
            return RUN_ID;
        }

        @Override
        public Optional<SchedulerJobRunRecord> findRun(UUID runId) {
            return run != null && run.id().equals(runId) ? Optional.of(run) : Optional.empty();
        }

        @Override
        public boolean tryAcquireLock(
                String concurrencyKey,
                UUID jobId,
                UUID runId,
                OffsetDateTime acquiredAt,
                OffsetDateTime staleBefore
        ) {
            return acquireLock;
        }

        @Override
        public void completeRun(
                UUID runId,
                String status,
                OffsetDateTime finishedAt,
                long durationMs,
                String summaryJson,
                String errorMessage
        ) {
            run = new SchedulerJobRunRecord(
                    run.id(),
                    run.jobId(),
                    run.triggerSource(),
                    status,
                    run.startedAt(),
                    finishedAt,
                    durationMs,
                    summaryJson,
                    errorMessage,
                    run.createdAt()
            );
        }

        @Override
        public void releaseLock(String concurrencyKey, UUID runId) {
            lockReleases.add(concurrencyKey + ":" + runId);
        }
    }

    private static class ImmediateTransactionTemplate extends TransactionTemplate {

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }

        @Override
        public void executeWithoutResult(java.util.function.Consumer<TransactionStatus> action) {
            action.accept(null);
        }
    }
}
