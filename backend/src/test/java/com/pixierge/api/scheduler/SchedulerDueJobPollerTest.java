package com.pixierge.api.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class SchedulerDueJobPollerTest {

  @Test
  void pollDelegatesToSchedulerService() {
    RecordingSchedulerRepository repository = new RecordingSchedulerRepository();
    SchedulerDueJobPoller poller =
        new SchedulerDueJobPoller(
            new SchedulerService(
                repository,
                new SchedulerJobRegistry(List.of()),
                new SyncTaskExecutor(),
                new ImmediateTransactionTemplate()));

    poller.poll();

    assertThat(repository.dueJobPolls).isEqualTo(1);
  }

  private static final class RecordingSchedulerRepository extends SchedulerRepository {

    private int dueJobPolls;

    private RecordingSchedulerRepository() {
      super(null);
    }

    @Override
    public List<SchedulerJobRecord> findDueJobs(java.time.OffsetDateTime now) {
      dueJobPolls++;
      return List.of();
    }
  }

  private static final class ImmediateTransactionTemplate extends TransactionTemplate {
    @Override
    public <T> T execute(TransactionCallback<T> action) {
      return action.doInTransaction(new SimpleTransactionStatus());
    }
  }
}
