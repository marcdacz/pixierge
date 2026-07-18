package com.pixierge.api.background;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Autowired;

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

    @Autowired
    public BackgroundJobService(BackgroundJobRepository repository, TransactionTemplate transactionTemplate) {
        this(repository, transactionTemplate, Clock.systemUTC());
    }

    BackgroundJobService(BackgroundJobRepository repository, TransactionTemplate transactionTemplate, Clock clock) {
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public UUID enqueue(BackgroundJobCreate create) {
        OffsetDateTime now = now();
        return transactionTemplate.execute(status -> repository.enqueue(create, now));
    }

    public List<BackgroundJobRecord> claimReadyJobs(int limit, String workerId) {
        OffsetDateTime now = now();
        return repository.claimReadyJobs(limit, workerId, now, now.plus(DEFAULT_LEASE_DURATION));
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

    private Duration retryDelay(int attempts) {
        long multiplier = 1L << Math.min(Math.max(0, attempts - 1), 6);
        Duration delay = BASE_RETRY_DELAY.multipliedBy(multiplier);
        return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
