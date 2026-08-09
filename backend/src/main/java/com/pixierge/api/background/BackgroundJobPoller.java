package com.pixierge.api.background;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "pixierge.background-jobs.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class BackgroundJobPoller {
  private static final Logger log = LoggerFactory.getLogger(BackgroundJobPoller.class);

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
    } catch (BadSqlGrammarException exception) {
      if (!isMissingBackgroundJobsTable(exception)) {
        throw exception;
      }
      log.debug("Background job polling skipped while the database schema is unavailable");
    } finally {
      dispatching.set(false);
    }
  }

  private boolean isMissingBackgroundJobsTable(BadSqlGrammarException exception) {
    SQLException sqlException = exception.getSQLException();
    return sqlException != null
        && "42P01".equals(sqlException.getSQLState())
        && sqlException.getMessage() != null
        && sqlException.getMessage().contains("background_jobs");
  }
}
