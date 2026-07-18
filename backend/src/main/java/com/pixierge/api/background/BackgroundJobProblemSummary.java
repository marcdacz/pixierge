package com.pixierge.api.background;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BackgroundJobProblemSummary(
        UUID id,
        String jobType,
        String status,
        int attempts,
        int maxAttempts,
        String lastErrorCode,
        String lastErrorMessage,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt
) {
}
