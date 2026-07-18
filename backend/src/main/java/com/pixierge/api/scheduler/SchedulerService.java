package com.pixierge.api.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_SUCCEEDED = "succeeded";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_SKIPPED = "skipped";
    private static final String TRIGGER_SCHEDULED = "scheduled";
    private static final String TRIGGER_MANUAL = "manual";

    private final SchedulerRepository repository;
    private final SchedulerJobRegistry registry;
    private final TaskExecutor taskExecutor;
    private final TransactionTemplate transactionTemplate;

    public SchedulerService(
            SchedulerRepository repository,
            SchedulerJobRegistry registry,
            @Qualifier("taskScheduler")
            TaskExecutor taskExecutor,
            TransactionTemplate transactionTemplate
    ) {
        this.repository = repository;
        this.registry = registry;
        this.taskExecutor = taskExecutor;
        this.transactionTemplate = transactionTemplate;
    }

    public List<SchedulerJobResponse> listJobs() {
        return repository.listJobs().stream().map(SchedulerJobResponse::from).toList();
    }

    public SchedulerJobResponse getJob(UUID jobId) {
        return SchedulerJobResponse.from(requireJob(jobId));
    }

    public SchedulerJobResponse updateJob(UUID jobId, UpdateSchedulerJobRequest request) {
        SchedulerJobRecord job = requireJob(jobId);
        boolean enabled = request.enabled() != null ? request.enabled() : job.enabled();
        String cronExpression = request.cronExpression() != null && !request.cronExpression().isBlank()
                ? request.cronExpression().trim()
                : job.cronExpression();
        String timezone = request.timezone() != null && !request.timezone().isBlank()
                ? request.timezone().trim()
                : job.timezone();

        try {
            CronSupport.parse(cronExpression);
            CronSupport.zoneId(timezone);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }

        OffsetDateTime nextRun = enabled
                ? CronSupport.nextRunAt(cronExpression, timezone, OffsetDateTime.now())
                : null;

        transactionTemplate.executeWithoutResult(status ->
                repository.updateJobSchedule(jobId, enabled, cronExpression, timezone, nextRun)
        );
        return getJob(jobId);
    }

    public SchedulerJobRunResponse runNow(UUID jobId) {
        return startRun(requireJob(jobId), TRIGGER_MANUAL);
    }

    public void pollDueJobs() {
        OffsetDateTime now = OffsetDateTime.now();
        for (SchedulerJobRecord job : repository.findDueJobs(now)) {
            try {
                startRun(job, TRIGGER_SCHEDULED);
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() == HttpStatus.CONFLICT) {
                    OffsetDateTime nextRun = CronSupport.nextRunAt(job.cronExpression(), job.timezone(), now);
                    transactionTemplate.executeWithoutResult(status -> {
                        repository.updateJobRunState(job.id(), job.lastRunAt(), STATUS_SKIPPED);
                        repository.advanceNextRunIfScheduleUnchanged(job, nextRun);
                    });
                    log.info("Skipped overlapping scheduled run for {}", job.jobKey());
                } else {
                    log.warn("Failed to start scheduled job {}: {}", job.jobKey(), exception.getMessage());
                }
            } catch (RuntimeException exception) {
                log.warn("Failed to start scheduled job {}", job.jobKey(), exception);
            }
        }
    }

    private SchedulerJobRunResponse startRun(SchedulerJobRecord job, String triggerSource) {
        OffsetDateTime startedAt = OffsetDateTime.now();

        UUID runId = transactionTemplate.execute(status -> {
            UUID createdRunId = repository.insertRun(job.id(), triggerSource, STATUS_RUNNING, startedAt);
            OffsetDateTime staleBefore = startedAt.minusSeconds(job.timeoutSeconds());
            if (!repository.tryAcquireLock(
                    job.concurrencyKey(),
                    job.id(),
                    createdRunId,
                    startedAt,
                    staleBefore
            )) {
                repository.completeRun(
                        createdRunId,
                        STATUS_SKIPPED,
                        OffsetDateTime.now(),
                        0L,
                        null,
                        "Another run is already in progress for concurrency key " + job.concurrencyKey()
                );
                return null;
            }
            repository.updateJobRunState(job.id(), startedAt, STATUS_RUNNING);
            if (TRIGGER_SCHEDULED.equals(triggerSource)) {
                OffsetDateTime nextRun = CronSupport.nextRunAt(job.cronExpression(), job.timezone(), startedAt);
                repository.advanceNextRunIfScheduleUnchanged(job, nextRun);
            }
            return createdRunId;
        });

        if (runId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Job is already running");
        }

        try {
            taskExecutor.execute(() -> executeRun(job.id(), runId));
        } catch (RuntimeException exception) {
            failUnsubmittedRun(job, runId, startedAt, exception);
            throw exception;
        }

        return repository.findRun(runId)
                .map(SchedulerJobRunResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Run not found"));
    }

    private void executeRun(UUID jobId, UUID runId) {
        SchedulerJobRecord job = repository.findJob(jobId).orElse(null);
        if (job == null) {
            return;
        }

        OffsetDateTime startedAt = repository.findRun(runId)
                .map(SchedulerJobRunRecord::startedAt)
                .orElse(OffsetDateTime.now());
        String status = STATUS_SUCCEEDED;
        String summaryJson = null;
        String errorMessage = null;

        try {
            SchedulerJobResult result = registry.requireHandler(job.jobKey()).execute(job);
            summaryJson = result.summaryJson();
        } catch (Exception exception) {
            status = STATUS_FAILED;
            errorMessage = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            log.warn("Scheduler job {} failed", job.jobKey(), exception);
        } finally {
            OffsetDateTime finishedAt = OffsetDateTime.now();
            long durationMs = Math.max(
                    0,
                    finishedAt.toInstant().toEpochMilli() - startedAt.toInstant().toEpochMilli()
            );
            String finalStatus = status;
            String finalSummary = summaryJson;
            String finalError = errorMessage;
            transactionTemplate.executeWithoutResult(tx -> {
                repository.completeRun(runId, finalStatus, finishedAt, durationMs, finalSummary, finalError);
                repository.updateJobRunState(jobId, finishedAt, finalStatus);
                repository.releaseLock(job.concurrencyKey(), runId);
            });
        }
    }

    private void failUnsubmittedRun(
            SchedulerJobRecord job,
            UUID runId,
            OffsetDateTime startedAt,
            RuntimeException exception
    ) {
        OffsetDateTime finishedAt = OffsetDateTime.now();
        long durationMs = Math.max(
                0,
                finishedAt.toInstant().toEpochMilli() - startedAt.toInstant().toEpochMilli()
        );
        String errorMessage = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        transactionTemplate.executeWithoutResult(tx -> {
            repository.completeRun(runId, STATUS_FAILED, finishedAt, durationMs, null, errorMessage);
            repository.updateJobRunState(job.id(), finishedAt, STATUS_FAILED);
            repository.releaseLock(job.concurrencyKey(), runId);
        });
    }

    private SchedulerJobRecord requireJob(UUID jobId) {
        return repository.findJob(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scheduler job not found"));
    }
}
