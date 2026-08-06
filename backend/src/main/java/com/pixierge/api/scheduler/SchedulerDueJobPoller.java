package com.pixierge.api.scheduler;

import static com.pixierge.api.scheduler.SchedulerConstants.DEFAULT_POLL_INTERVAL_MS;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "pixierge.scheduler.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SchedulerDueJobPoller {

  private final SchedulerService schedulerService;

  public SchedulerDueJobPoller(SchedulerService schedulerService) {
    this.schedulerService = schedulerService;
  }

  @Scheduled(
      fixedDelayString = "${pixierge.scheduler.poll-interval-ms:" + DEFAULT_POLL_INTERVAL_MS + "}")
  public void poll() {
    schedulerService.pollDueJobs();
  }
}
