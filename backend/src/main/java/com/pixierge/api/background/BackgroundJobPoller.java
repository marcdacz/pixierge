package com.pixierge.api.background;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "pixierge.background-jobs.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class BackgroundJobPoller {

  private final BackgroundJobWorker worker;
  private final BackgroundJobWorker metadataWorker;
  private final int claimBatchSize;
  private final AtomicBoolean dispatching = new AtomicBoolean();

  public BackgroundJobPoller(
      @Qualifier("backgroundJobWorker") BackgroundJobWorker worker,
      @Qualifier("metadataBackgroundJobWorker") BackgroundJobWorker metadataWorker,
      @Value("${pixierge.background-jobs.claim-batch-size:25}") int claimBatchSize) {
    this.worker = worker;
    this.metadataWorker = metadataWorker;
    this.claimBatchSize = Math.max(1, claimBatchSize);
  }

  @Scheduled(fixedDelayString = "${pixierge.background-jobs.poll-interval-ms:2000}")
  public void poll() {
    dispatch();
  }

  @EventListener
  public void onJobEnqueued(BackgroundJobEnqueuedEvent event) {
    dispatch();
  }

  private void dispatch() {
    if (!dispatching.compareAndSet(false, true)) {
      return;
    }
    try {
      worker.pollBatch(claimBatchSize);
      metadataWorker.pollBatch(claimBatchSize);
    } finally {
      dispatching.set(false);
    }
  }
}
