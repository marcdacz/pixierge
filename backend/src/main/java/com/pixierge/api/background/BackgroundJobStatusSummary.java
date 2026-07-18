package com.pixierge.api.background;

import java.time.OffsetDateTime;

public record BackgroundJobStatusSummary(
        String jobType,
        String status,
        long count,
        OffsetDateTime oldestCreatedAt,
        OffsetDateTime oldestNextRunAt,
        OffsetDateTime latestUpdatedAt
) {
}
