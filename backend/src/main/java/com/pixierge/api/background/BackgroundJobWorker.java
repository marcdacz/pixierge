package com.pixierge.api.background;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BackgroundJobWorker {

    private static final Logger log = LoggerFactory.getLogger(BackgroundJobWorker.class);

    private final BackgroundJobService jobService;
    private final Map<String, BackgroundJobHandler> handlers;
    private final TaskExecutor taskExecutor;
    private final Semaphore workerSlots;
    private final String includedJobType;
    private final String excludedJobType;
    private final ApplicationEventPublisher eventPublisher;

    public BackgroundJobWorker(
            BackgroundJobService jobService,
            List<BackgroundJobHandler> handlers,
            TaskExecutor taskExecutor,
            int maxConcurrentJobs,
            String includedJobType,
            String excludedJobType,
            ApplicationEventPublisher eventPublisher
    ) {
        this.jobService = jobService;
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(BackgroundJobHandler::jobType, Function.identity()));
        this.taskExecutor = taskExecutor;
        this.workerSlots = new Semaphore(Math.max(1, maxConcurrentJobs));
        this.includedJobType = includedJobType;
        this.excludedJobType = excludedJobType;
        this.eventPublisher = eventPublisher;
    }

    BackgroundJobWorker(BackgroundJobService jobService, List<BackgroundJobHandler> handlers) {
        this(jobService, handlers, Runnable::run, 1, null, null, null);
    }

    public int pollBatch(int limit) {
        int claimLimit = Math.min(Math.max(1, limit), workerSlots.availablePermits());
        if (claimLimit == 0) {
            return 0;
        }
        String workerId = "pixierge-" + UUID.randomUUID();
        List<BackgroundJobRecord> jobs = jobService.claimReadyJobs(
                claimLimit,
                workerId,
                includedJobType,
                excludedJobType
        );
        for (BackgroundJobRecord job : jobs) {
            if (!workerSlots.tryAcquire()) {
                break;
            }
            try {
                taskExecutor.execute(() -> {
                    try {
                        handle(job, workerId);
                    } finally {
                        workerSlots.release();
                        notifyWorkers();
                    }
                });
            } catch (RejectedExecutionException exception) {
                workerSlots.release();
                jobService.fail(job.id(), workerId, "executor_rejected", "Background worker rejected the job");
            }
        }
        return jobs.size();
    }

    private void handle(BackgroundJobRecord job, String workerId) {
        BackgroundJobHandler handler = handlers.get(job.jobType());
        if (handler == null) {
            jobService.failTerminal(job.id(), workerId, "handler_missing", "No handler registered for " + job.jobType());
            return;
        }
        try {
            handler.handle(job);
            jobService.complete(job.id(), workerId, job.progressJson());
            try {
                handler.afterComplete(job);
            } catch (Exception exception) {
                log.warn("Background job {} completed, but post-completion work failed", job.id(), exception);
            }
        } catch (Exception exception) {
            log.warn("Background job {} failed", job.id(), exception);
            jobService.fail(
                    job.id(),
                    workerId,
                    exception.getClass().getSimpleName(),
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()
            );
        }
    }

    private void notifyWorkers() {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new BackgroundJobEnqueuedEvent());
        }
    }
}
