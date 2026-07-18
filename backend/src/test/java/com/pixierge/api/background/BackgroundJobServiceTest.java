package com.pixierge.api.background;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BackgroundJobServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-18T00:00:00Z"), ZoneOffset.UTC);
    private static final String WORKER_ID = "worker-1";

    private final FakeBackgroundJobRepository repository = new FakeBackgroundJobRepository();
    private final BackgroundJobService service = new BackgroundJobService(
            repository,
            new ImmediateTransactionTemplate(),
            CLOCK
    );

    @Test
    void failRetriesRunningJobUntilMaxAttemptsIsReached() {
        UUID jobId = service.enqueue(job("library-catalog-root", "library:1", 2));

        BackgroundJobRecord firstClaim = service.claimReadyJobs(1, WORKER_ID).getFirst();
        service.fail(firstClaim.id(), WORKER_ID, "io_error", "Temporary failure");

        BackgroundJobRecord retry = repository.find(jobId).orElseThrow();
        assertThat(retry.status()).isEqualTo(BackgroundJobRepository.STATUS_PENDING);
        assertThat(retry.attempts()).isEqualTo(1);
        assertThat(retry.lastErrorCode()).isEqualTo("io_error");
        assertThat(retry.nextRunAt()).isAfter(OffsetDateTime.now(CLOCK));

        repository.makeReady(jobId);
        BackgroundJobRecord secondClaim = service.claimReadyJobs(1, WORKER_ID).getFirst();
        service.fail(secondClaim.id(), WORKER_ID, "io_error", "Still failing");

        BackgroundJobRecord deadLetter = repository.find(jobId).orElseThrow();
        assertThat(deadLetter.status()).isEqualTo(BackgroundJobRepository.STATUS_DEAD_LETTER);
        assertThat(deadLetter.attempts()).isEqualTo(2);
        assertThat(deadLetter.completedAt()).isEqualTo(OffsetDateTime.now(CLOCK));
    }

    @Test
    void wrongWorkerCannotFailClaimedJob() {
        UUID jobId = service.enqueue(job("library-catalog-root", "library:1", 3));
        BackgroundJobRecord claimed = service.claimReadyJobs(1, WORKER_ID).getFirst();

        service.fail(claimed.id(), "worker-2", "wrong_worker", "Should be ignored");

        BackgroundJobRecord unchanged = repository.find(jobId).orElseThrow();
        assertThat(unchanged.status()).isEqualTo(BackgroundJobRepository.STATUS_RUNNING);
        assertThat(unchanged.lockedBy()).isEqualTo(WORKER_ID);
        assertThat(unchanged.lastErrorCode()).isNull();
    }

    @Test
    void workerCompletesJobWithRegisteredHandler() {
        UUID jobId = service.enqueue(job("filesystem-change-event", "root:1:path", 3));
        RecordingHandler handler = new RecordingHandler("filesystem-change-event", false);
        BackgroundJobWorker worker = new BackgroundJobWorker(service, List.of(handler));

        int processed = worker.pollBatch(5);

        assertThat(processed).isEqualTo(1);
        assertThat(handler.handledJobIds).containsExactly(jobId);
        assertThat(repository.find(jobId).orElseThrow().status()).isEqualTo(BackgroundJobRepository.STATUS_SUCCEEDED);
    }

    @Test
    void workerDeadLettersJobWithoutHandler() {
        UUID jobId = service.enqueue(job("unknown-job", "unknown:1", 3));
        BackgroundJobWorker worker = new BackgroundJobWorker(service, List.of());

        int processed = worker.pollBatch(5);

        BackgroundJobRecord job = repository.find(jobId).orElseThrow();
        assertThat(processed).isEqualTo(1);
        assertThat(job.status()).isEqualTo(BackgroundJobRepository.STATUS_DEAD_LETTER);
        assertThat(job.lastErrorCode()).isEqualTo("handler_missing");
    }

    @Test
    void enqueueReusesActiveDedupeKey() {
        BackgroundJobCreate first = job("library-catalog-root", "library:1:root:1", 3);
        BackgroundJobCreate duplicate = job("library-catalog-root", "library:1:root:1", 3);

        UUID firstId = service.enqueue(first);
        UUID secondId = service.enqueue(duplicate);

        assertThat(secondId).isEqualTo(firstId);
        assertThat(repository.jobs).hasSize(1);
    }

    @Test
    void claimSkipsSecondJobWithSameConcurrencyKey() {
        UUID firstId = service.enqueue(job("library-catalog-root", "library:1:root:1", 3));
        UUID secondId = service.enqueue(job("library-catalog-subtree", "library:1:root:1:/new", 3));

        List<BackgroundJobRecord> claimed = service.claimReadyJobs(5, WORKER_ID);

        assertThat(claimed).extracting(BackgroundJobRecord::id).containsExactly(firstId);
        assertThat(repository.find(firstId).orElseThrow().status()).isEqualTo(BackgroundJobRepository.STATUS_RUNNING);
        assertThat(repository.find(secondId).orElseThrow().status()).isEqualTo(BackgroundJobRepository.STATUS_PENDING);
    }

    private BackgroundJobCreate job(String jobType, String dedupeKey, int maxAttempts) {
        return new BackgroundJobCreate(
                jobType,
                "{\"ok\":true}",
                0,
                maxAttempts,
                OffsetDateTime.now(CLOCK),
                "library:1",
                dedupeKey
        );
    }

    private static final class RecordingHandler implements BackgroundJobHandler {

        private final String jobType;
        private final boolean fail;
        private final List<UUID> handledJobIds = new ArrayList<>();

        private RecordingHandler(String jobType, boolean fail) {
            this.jobType = jobType;
            this.fail = fail;
        }

        @Override
        public String jobType() {
            return jobType;
        }

        @Override
        public void handle(BackgroundJobRecord job) {
            handledJobIds.add(job.id());
            if (fail) {
                throw new IllegalStateException("handler failed");
            }
        }
    }

    private static class FakeBackgroundJobRepository extends BackgroundJobRepository {

        private final List<BackgroundJobRecord> jobs = new ArrayList<>();

        private FakeBackgroundJobRepository() {
            super(null);
        }

        @Override
        public UUID enqueue(BackgroundJobCreate create, OffsetDateTime now) {
            Optional<BackgroundJobRecord> existing = findActiveByDedupeKey(create.dedupeKey());
            if (existing.isPresent()) {
                return existing.get().id();
            }
            UUID id = UUID.randomUUID();
            jobs.add(new BackgroundJobRecord(
                    id,
                    create.jobType(),
                    create.payloadJson(),
                    STATUS_PENDING,
                    create.priority(),
                    0,
                    create.maxAttempts(),
                    create.nextRunAt() == null ? now : create.nextRunAt(),
                    null,
                    null,
                    create.concurrencyKey(),
                    create.dedupeKey(),
                    null,
                    null,
                    null,
                    now,
                    now,
                    null
            ));
            return id;
        }

        @Override
        public List<BackgroundJobRecord> claimReadyJobs(int limit, String workerId, OffsetDateTime now, OffsetDateTime leaseUntil) {
            List<String> claimedKeys = new ArrayList<>();
            List<BackgroundJobRecord> candidates = jobs.stream()
                    .filter(job -> STATUS_PENDING.equals(job.status()))
                    .filter(job -> !job.nextRunAt().isAfter(now))
                    .filter(job -> job.attempts() < job.maxAttempts())
                    .sorted(Comparator.comparing(BackgroundJobRecord::priority).reversed()
                            .thenComparing(BackgroundJobRecord::createdAt))
                    .toList();
            List<BackgroundJobRecord> ready = new ArrayList<>();
            for (BackgroundJobRecord job : candidates) {
                if (ready.size() >= limit) {
                    break;
                }
                if (claimedKeys.contains(job.concurrencyKey())) {
                    continue;
                }
                claimedKeys.add(job.concurrencyKey());
                ready.add(job);
            }
            for (BackgroundJobRecord job : ready) {
                replace(job.id(), copy(
                        job,
                        STATUS_RUNNING,
                        job.attempts() + 1,
                        leaseUntil,
                        workerId,
                        job.nextRunAt(),
                        job.progressJson(),
                        job.lastErrorCode(),
                        job.lastErrorMessage(),
                        now,
                        job.completedAt()
                ));
            }
            return ready.stream()
                    .map(job -> find(job.id()).orElseThrow())
                    .toList();
        }

        @Override
        public void heartbeat(UUID jobId, String workerId, OffsetDateTime leaseUntil, OffsetDateTime now) {
            find(jobId)
                    .filter(job -> STATUS_RUNNING.equals(job.status()))
                    .filter(job -> workerId.equals(job.lockedBy()))
                    .ifPresent(job -> replace(jobId, copy(
                            job,
                            job.status(),
                            job.attempts(),
                            leaseUntil,
                            workerId,
                            job.nextRunAt(),
                            job.progressJson(),
                            job.lastErrorCode(),
                            job.lastErrorMessage(),
                            now,
                            job.completedAt()
                    )));
        }

        @Override
        public void complete(UUID jobId, String workerId, String progressJson, OffsetDateTime now) {
            find(jobId)
                    .filter(job -> STATUS_RUNNING.equals(job.status()))
                    .filter(job -> workerId.equals(job.lockedBy()))
                    .ifPresent(job -> replace(jobId, copy(
                            job,
                            STATUS_SUCCEEDED,
                            job.attempts(),
                            null,
                            null,
                            job.nextRunAt(),
                            progressJson,
                            job.lastErrorCode(),
                            job.lastErrorMessage(),
                            now,
                            now
                    )));
        }

        @Override
        public void retry(UUID jobId, String workerId, String errorCode, String errorMessage, OffsetDateTime nextRunAt, OffsetDateTime now) {
            find(jobId)
                    .filter(job -> STATUS_RUNNING.equals(job.status()))
                    .filter(job -> workerId.equals(job.lockedBy()))
                    .ifPresent(job -> replace(jobId, copy(
                            job,
                            STATUS_PENDING,
                            job.attempts(),
                            null,
                            null,
                            nextRunAt,
                            job.progressJson(),
                            errorCode,
                            errorMessage,
                            now,
                            job.completedAt()
                    )));
        }

        @Override
        public void deadLetter(UUID jobId, String workerId, String errorCode, String errorMessage, OffsetDateTime now) {
            find(jobId)
                    .filter(job -> STATUS_RUNNING.equals(job.status()))
                    .filter(job -> workerId.equals(job.lockedBy()))
                    .ifPresent(job -> replace(jobId, copy(
                            job,
                            STATUS_DEAD_LETTER,
                            job.attempts(),
                            null,
                            null,
                            job.nextRunAt(),
                            job.progressJson(),
                            errorCode,
                            errorMessage,
                            now,
                            now
                    )));
        }

        @Override
        public Optional<BackgroundJobRecord> find(UUID jobId) {
            return jobs.stream()
                    .filter(job -> job.id().equals(jobId))
                    .findFirst();
        }

        @Override
        public Optional<BackgroundJobRecord> findActiveByDedupeKey(String dedupeKey) {
            if (dedupeKey == null) {
                return Optional.empty();
            }
            return jobs.stream()
                    .filter(job -> dedupeKey.equals(job.dedupeKey()))
                    .filter(job -> STATUS_PENDING.equals(job.status()) || STATUS_RUNNING.equals(job.status()))
                    .findFirst();
        }

        private void makeReady(UUID jobId) {
            OffsetDateTime now = OffsetDateTime.now(CLOCK);
            find(jobId).ifPresent(job -> replace(jobId, copy(
                    job,
                    job.status(),
                    job.attempts(),
                    job.leaseUntil(),
                    job.lockedBy(),
                    now,
                    job.progressJson(),
                    job.lastErrorCode(),
                    job.lastErrorMessage(),
                    now,
                    job.completedAt()
            )));
        }

        private void replace(UUID jobId, BackgroundJobRecord replacement) {
            for (int index = 0; index < jobs.size(); index++) {
                if (jobs.get(index).id().equals(jobId)) {
                    jobs.set(index, replacement);
                    return;
                }
            }
        }

        private BackgroundJobRecord copy(
                BackgroundJobRecord job,
                String status,
                int attempts,
                OffsetDateTime leaseUntil,
                String lockedBy,
                OffsetDateTime nextRunAt,
                String progressJson,
                String errorCode,
                String errorMessage,
                OffsetDateTime updatedAt,
                OffsetDateTime completedAt
        ) {
            return new BackgroundJobRecord(
                    job.id(),
                    job.jobType(),
                    job.payloadJson(),
                    status,
                    job.priority(),
                    attempts,
                    job.maxAttempts(),
                    nextRunAt,
                    leaseUntil,
                    lockedBy,
                    job.concurrencyKey(),
                    job.dedupeKey(),
                    progressJson,
                    errorCode,
                    errorMessage,
                    job.createdAt(),
                    updatedAt,
                    completedAt
            );
        }
    }

    private static class ImmediateTransactionTemplate extends TransactionTemplate {
        @Override
        public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }
    }
}
