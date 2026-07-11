package com.pixierge.api.scheduler;

public record UpdateSchedulerJobRequest(
        Boolean enabled,
        String cronExpression,
        String timezone
) {
}
