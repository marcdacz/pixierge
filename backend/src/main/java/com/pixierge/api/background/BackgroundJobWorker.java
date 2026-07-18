package com.pixierge.api.background;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BackgroundJobWorker {

    private static final Logger log = LoggerFactory.getLogger(BackgroundJobWorker.class);

    private final BackgroundJobService jobService;
    private final Map<String, BackgroundJobHandler> handlers;

    public BackgroundJobWorker(BackgroundJobService jobService, List<BackgroundJobHandler> handlers) {
        this.jobService = jobService;
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(BackgroundJobHandler::jobType, Function.identity()));
    }

    public int pollBatch(int limit) {
        String workerId = "pixierge-" + UUID.randomUUID();
        List<BackgroundJobRecord> jobs = jobService.claimReadyJobs(limit, workerId);
        for (BackgroundJobRecord job : jobs) {
            handle(job, workerId);
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
}
