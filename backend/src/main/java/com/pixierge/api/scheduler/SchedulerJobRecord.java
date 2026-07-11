package com.pixierge.api.scheduler;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SchedulerJobRecord(
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
        String concurrencyKey,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
