package com.pixierge.api.background;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class BackgroundJobWorkerTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-30T00:00:00Z");

    @Test
    void pollBatchCompletesHandledJobsAndContinuesWhenAfterCompleteFails() {
        RecordingJobService jobService = new RecordingJobService(job("known"));
        RecordingHandler handler = new RecordingHandler("known");
        handler.afterCompleteFailure = new IllegalStateException("follow-up failed");
        BackgroundJobWorker worker = new BackgroundJobWorker(jobService, List.of(handler));

        int claimed = worker.pollBatch(10);

        assertThat(claimed).isEqualTo(1);
        assertThat(handler.handledJobIds).containsExactly(jobService.claimedJobs.getFirst().id());
        assertThat(jobService.completedJobIds).containsExactly(jobService.claimedJobs.getFirst().id());
        assertThat(jobService.failedJobIds).isEmpty();
    }

    @Test
    void pollBatchFailsTerminalWhenNoHandlerIsRegistered() {
        RecordingJobService jobService = new RecordingJobService(job("missing"));
        BackgroundJobWorker worker = new BackgroundJobWorker(jobService, List.of());

        worker.pollBatch(1);

        assertThat(jobService.terminalFailures).containsExactly("handler_missing:No handler registered for missing");
        assertThat(jobService.completedJobIds).isEmpty();
    }

    @Test
    void pollBatchRetriesWhenHandlerFails() {
        RecordingJobService jobService = new RecordingJobService(job("known"));
        RecordingHandler handler = new RecordingHandler("known");
        handler.handleFailure = new IllegalArgumentException("bad payload");
        BackgroundJobWorker worker = new BackgroundJobWorker(jobService, List.of(handler));

        worker.pollBatch(1);

        assertThat(jobService.failures).containsExactly("IllegalArgumentException:bad payload");
        assertThat(jobService.completedJobIds).isEmpty();
    }

    @Test
    void pollBatchMarksJobFailedWhenExecutorRejectsIt() {
        RecordingJobService jobService = new RecordingJobService(job("known"));
        TaskExecutor rejectingExecutor = task -> {
            throw new RejectedExecutionException("no threads");
        };
        BackgroundJobWorker worker = new BackgroundJobWorker(
                jobService,
                List.of(new RecordingHandler("known")),
                rejectingExecutor,
                2,
                "included-type",
                "excluded-type",
                null
        );

        int claimed = worker.pollBatch(5);

        assertThat(claimed).isEqualTo(1);
        assertThat(jobService.lastClaimLimit).isEqualTo(2);
        assertThat(jobService.lastIncludedJobType).isEqualTo("included-type");
        assertThat(jobService.lastExcludedJobType).isEqualTo("excluded-type");
        assertThat(jobService.failures).containsExactly("executor_rejected:Background worker rejected the job");
    }

    private static BackgroundJobRecord job(String jobType) {
        return new BackgroundJobRecord(
                UUID.randomUUID(),
                jobType,
                "{\"ok\":true}",
                BackgroundJobRepository.STATUS_RUNNING,
                0,
                1,
                3,
                NOW,
                NOW.plusMinutes(5),
                null,
                jobType,
                jobType + ":1",
                "{\"progress\":true}",
                null,
                null,
                NOW,
                NOW,
                null
        );
    }

    private static final class RecordingJobService extends BackgroundJobService {

        private final List<BackgroundJobRecord> jobs;
        private final List<BackgroundJobRecord> claimedJobs = new ArrayList<>();
        private final List<UUID> completedJobIds = new ArrayList<>();
        private final List<UUID> failedJobIds = new ArrayList<>();
        private final List<String> failures = new ArrayList<>();
        private final List<String> terminalFailures = new ArrayList<>();
        private int lastClaimLimit;
        private String lastIncludedJobType;
        private String lastExcludedJobType;

        private RecordingJobService(BackgroundJobRecord... jobs) {
            super(new BackgroundJobRepository(null), new TransactionTemplate());
            this.jobs = List.of(jobs);
        }

        @Override
        public List<BackgroundJobRecord> claimReadyJobs(
                int limit,
                String workerId,
                String includedJobType,
                String excludedJobType
        ) {
            lastClaimLimit = limit;
            lastIncludedJobType = includedJobType;
            lastExcludedJobType = excludedJobType;
            claimedJobs.addAll(jobs.stream().limit(Math.max(0, limit)).toList());
            return jobs.stream()
                    .limit(Math.max(0, limit))
                    .map(job -> new BackgroundJobRecord(
                            job.id(),
                            job.jobType(),
                            job.payloadJson(),
                            job.status(),
                            job.priority(),
                            job.attempts(),
                            job.maxAttempts(),
                            job.nextRunAt(),
                            job.leaseUntil(),
                            workerId,
                            job.concurrencyKey(),
                            job.dedupeKey(),
                            job.progressJson(),
                            job.lastErrorCode(),
                            job.lastErrorMessage(),
                            job.createdAt(),
                            job.updatedAt(),
                            job.completedAt(),
                            job.startedAt()
                    ))
                    .toList();
        }

        @Override
        public void complete(UUID jobId, String workerId, String progressJson) {
            completedJobIds.add(jobId);
        }

        @Override
        public void fail(UUID jobId, String workerId, String errorCode, String errorMessage) {
            failedJobIds.add(jobId);
            failures.add(errorCode + ":" + errorMessage);
        }

        @Override
        public void failTerminal(UUID jobId, String workerId, String errorCode, String errorMessage) {
            terminalFailures.add(errorCode + ":" + errorMessage);
        }
    }

    private static final class RecordingHandler implements BackgroundJobHandler {

        private final String jobType;
        private final List<UUID> handledJobIds = new ArrayList<>();
        private Exception handleFailure;
        private Exception afterCompleteFailure;

        private RecordingHandler(String jobType) {
            this.jobType = jobType;
        }

        @Override
        public String jobType() {
            return jobType;
        }

        @Override
        public void handle(BackgroundJobRecord job) throws Exception {
            handledJobIds.add(job.id());
            if (handleFailure != null) {
                throw handleFailure;
            }
        }

        @Override
        public void afterComplete(BackgroundJobRecord job) throws Exception {
            if (afterCompleteFailure != null) {
                throw afterCompleteFailure;
            }
        }
    }
}
