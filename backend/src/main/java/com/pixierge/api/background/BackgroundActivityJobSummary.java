package com.pixierge.api.background;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BackgroundActivityJobSummary(
        UUID id,
        String jobType,
        String status,
        String batchLabel,
        int fileCount,
        int attempts,
        int maxAttempts,
        String lockedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
