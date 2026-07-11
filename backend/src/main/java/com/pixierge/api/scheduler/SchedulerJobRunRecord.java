package com.pixierge.api.scheduler;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SchedulerJobRunRecord(
        UUID id,
        UUID jobId,
        String triggerSource,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Long durationMs,
        String summaryJson,
        String errorMessage,
        OffsetDateTime createdAt
) {
}
