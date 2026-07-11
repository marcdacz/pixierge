package com.pixierge.api.scheduler;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SchedulerJobRunResponse(
        UUID id,
        UUID jobId,
        String triggerSource,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Long durationMs,
        String summaryJson,
        String errorMessage
) {
    static SchedulerJobRunResponse from(SchedulerJobRunRecord record) {
        return new SchedulerJobRunResponse(
                record.id(),
                record.jobId(),
                record.triggerSource(),
                record.status(),
                record.startedAt(),
                record.finishedAt(),
                record.durationMs(),
                record.summaryJson(),
                record.errorMessage()
        );
    }
}
