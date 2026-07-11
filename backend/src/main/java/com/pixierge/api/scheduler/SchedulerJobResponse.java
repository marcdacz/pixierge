package com.pixierge.api.scheduler;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SchedulerJobResponse(
        UUID id,
        String jobKey,
        String displayName,
        String description,
        String ownerType,
        boolean enabled,
        String cronExpression,
        String timezone,
        OffsetDateTime nextRunAt,
        OffsetDateTime lastRunAt,
        String lastStatus,
        int timeoutSeconds,
        String concurrencyKey
) {
    static SchedulerJobResponse from(SchedulerJobRecord record) {
        return new SchedulerJobResponse(
                record.id(),
                record.jobKey(),
                record.displayName(),
                record.description(),
                record.ownerType(),
                record.enabled(),
                record.cronExpression(),
                record.timezone(),
                record.nextRunAt(),
                record.lastRunAt(),
                record.lastStatus(),
                record.timeoutSeconds(),
                record.concurrencyKey()
        );
    }
}
