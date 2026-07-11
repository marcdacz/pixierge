package com.pixierge.api.scheduler;

public record SchedulerJobDefinition(
        String jobKey,
        String displayName,
        String description,
        String defaultCronExpression,
        String defaultTimezone,
        boolean enabledByDefault,
        int timeoutSeconds,
        String concurrencyKey,
        SchedulerJobHandler handler
) {
}
