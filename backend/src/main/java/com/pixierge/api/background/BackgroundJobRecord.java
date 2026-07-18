package com.pixierge.api.background;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BackgroundJobRecord(
        UUID id,
        String jobType,
        String payloadJson,
        String status,
        int priority,
        int attempts,
        int maxAttempts,
        OffsetDateTime nextRunAt,
        OffsetDateTime leaseUntil,
        String lockedBy,
        String concurrencyKey,
        String dedupeKey,
        String progressJson,
        String lastErrorCode,
        String lastErrorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt
) {
}
