package com.pixierge.api.background;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;

class BackgroundJobPollerTest {
  @Test
  void skipsPollingWhenBackgroundJobsTableIsTemporarilyUnavailable() {
    BackgroundJobPoller poller =
        new BackgroundJobPoller(
            new FailingWorker(
                new BadSqlGrammarException(
                    "claim",
                    "select * from background_jobs",
                    new SQLException(
                        "ERROR: relation \"background_jobs\" does not exist", "42P01"))),
            new CountingWorker(),
            10);

    assertThatCode(poller::poll).doesNotThrowAnyException();
  }

  @Test
  void stillSurfacesOtherPollingSqlErrors() {
    BackgroundJobPoller poller =
        new BackgroundJobPoller(
            new FailingWorker(
                new BadSqlGrammarException(
                    "claim", "select * from background_jobs", new SQLException("syntax", "42601"))),
            new CountingWorker(),
            10);

    assertThatThrownBy(poller::poll).isInstanceOf(BadSqlGrammarException.class);
  }

  @Test
  void dispatchesBothWorkersWhenSchemaIsAvailable() {
    CountingWorker regular = new CountingWorker();
    CountingWorker metadata = new CountingWorker();
    BackgroundJobPoller poller = new BackgroundJobPoller(regular, metadata, 10);

    poller.poll();

    assertThat(regular.polls).isEqualTo(1);
    assertThat(metadata.polls).isEqualTo(1);
  }

  private static final class CountingWorker extends BackgroundJobWorker {
    private int polls;

    CountingWorker() {
      super(mock(BackgroundJobService.class), List.of());
    }

    @Override
    public int pollBatch(int limit) {
      polls += 1;
      return 0;
    }
  }

  private static final class FailingWorker extends BackgroundJobWorker {
    private final RuntimeException exception;

    FailingWorker(RuntimeException exception) {
      super(mock(BackgroundJobService.class), List.of());
      this.exception = exception;
    }

    @Override
    public int pollBatch(int limit) {
      throw exception;
    }
  }
}
