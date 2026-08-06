package com.pixierge.api.background;

import java.time.Duration;

/** Signals that a job should be put back in the queue without consuming a retry attempt. */
public class BackgroundJobDeferredException extends RuntimeException {

  private final Duration delay;

  public BackgroundJobDeferredException(String message, Duration delay) {
    super(message);
    this.delay = delay;
  }

  Duration delay() {
    return delay;
  }
}
