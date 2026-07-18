package com.pixierge.api.background;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "pixierge.background-jobs.enabled", havingValue = "true")
public class BackgroundJobPoller {

    private final BackgroundJobWorker worker;
    private final int claimBatchSize;

    public BackgroundJobPoller(
            BackgroundJobWorker worker,
            @Value("${pixierge.background-jobs.claim-batch-size:25}") int claimBatchSize
    ) {
        this.worker = worker;
        this.claimBatchSize = Math.max(1, claimBatchSize);
    }

    @Scheduled(fixedDelayString = "${pixierge.background-jobs.poll-interval-ms:2000}")
    public void poll() {
        worker.pollBatch(claimBatchSize);
    }
}
