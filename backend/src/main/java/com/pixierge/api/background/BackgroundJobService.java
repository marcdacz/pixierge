package com.pixierge.api.background;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class BackgroundJobService {

    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofMinutes(5);
    private static final Duration BASE_RETRY_DELAY = Duration.ofSeconds(5);
    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(5);

    private final BackgroundJobRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public BackgroundJobService(
            BackgroundJobRepository repository,
            TransactionTemplate transactionTemplate,
            ApplicationEventPublisher eventPublisher
    ) {
        this(repository, transactionTemplate, Clock.systemUTC(), eventPublisher);
    }

    public BackgroundJobService(BackgroundJobRepository repository, TransactionTemplate transactionTemplate) {
        this(repository, transactionTemplate, Clock.systemUTC(), null);
    }

    BackgroundJobService(BackgroundJobRepository repository, TransactionTemplate transactionTemplate, Clock clock) {
        this(repository, transactionTemplate, clock, null);
    }

    BackgroundJobService(
            BackgroundJobRepository repository,
            TransactionTemplate transactionTemplate,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    public UUID enqueue(BackgroundJobCreate create) {
        OffsetDateTime now = now();
        UUID id = transactionTemplate.execute(status -> repository.enqueue(create, now));
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new BackgroundJobEnqueuedEvent());
        }
        return id;
    }

    public List<BackgroundJobRecord> claimReadyJobs(int limit, String workerId) {
        return claimReadyJobs(limit, workerId, null, null);
    }

    public List<BackgroundJobRecord> claimReadyJobs(
            int limit,
            String workerId,
            String includedJobType,
            String excludedJobType
    ) {
        OffsetDateTime now = now();
        if (includedJobType == null && excludedJobType == null) {
            return repository.claimReadyJobs(limit, workerId, now, now.plus(DEFAULT_LEASE_DURATION));
        }
        return repository.claimReadyJobs(
                limit,
                workerId,
                now,
                now.plus(DEFAULT_LEASE_DURATION),
                includedJobType,
                excludedJobType
        );
    }

    public void heartbeat(UUID jobId, String workerId) {
        OffsetDateTime now = now();
        transactionTemplate.executeWithoutResult(status ->
                repository.heartbeat(jobId, workerId, now.plus(DEFAULT_LEASE_DURATION), now)
        );
    }

    public void complete(UUID jobId, String workerId, String progressJson) {
        OffsetDateTime now = now();
        transactionTemplate.executeWithoutResult(status ->
                repository.complete(jobId, workerId, progressJson, now)
        );
    }

    public void fail(UUID jobId, String workerId, String errorCode, String errorMessage) {
        OffsetDateTime now = now();
        transactionTemplate.executeWithoutResult(status -> {
            BackgroundJobRecord job = repository.find(jobId).orElse(null);
            if (job == null || !BackgroundJobRepository.STATUS_RUNNING.equals(job.status())) {
                return;
            }
            if (!workerId.equals(job.lockedBy())) {
                return;
            }
            if (job.attempts() >= job.maxAttempts()) {
                repository.deadLetter(jobId, workerId, errorCode, errorMessage, now);
                return;
            }
            repository.retry(jobId, workerId, errorCode, errorMessage, now.plus(retryDelay(job.attempts())), now);
        });
    }

    public void failTerminal(UUID jobId, String workerId, String errorCode, String errorMessage) {
        OffsetDateTime now = now();
        transactionTemplate.executeWithoutResult(status ->
                repository.deadLetter(jobId, workerId, errorCode, errorMessage, now)
        );
    }

    public void cancel(UUID jobId) {
        OffsetDateTime now = now();
        transactionTemplate.executeWithoutResult(status -> repository.cancel(jobId, now));
    }

    public boolean hasActiveJobs(String jobType, String dedupeKeyPrefix, UUID excludedJobId) {
        return transactionTemplate.execute(status -> repository.hasActiveJobs(jobType, dedupeKeyPrefix, excludedJobId));
    }

    public List<BackgroundJobStatusSummary> summarizeByTypeAndStatus() {
        return transactionTemplate.execute(status -> repository.summarizeByTypeAndStatus());
    }

    public List<BackgroundJobProblemSummary> latestProblemJobs(int limit) {
        return transactionTemplate.execute(status -> repository.latestProblemJobs(limit));
    }

    public List<BackgroundJobRecord> deadLetterJobs(String jobType, int limit) {
        return transactionTemplate.execute(status -> repository.deadLetterJobs(jobType, limit));
    }

    public List<BackgroundJobRecord> latestJobs(int limit) {
        return transactionTemplate.execute(status -> repository.latestJobs(limit));
    }

    private Duration retryDelay(int attempts) {
        long multiplier = 1L << Math.min(Math.max(0, attempts - 1), 6);
        Duration delay = BASE_RETRY_DELAY.multipliedBy(multiplier);
        return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
