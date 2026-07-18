package com.pixierge.api.background;

import java.time.OffsetDateTime;

public record BackgroundJobCreate(
        String jobType,
        String payloadJson,
        int priority,
        int maxAttempts,
        OffsetDateTime nextRunAt,
        String concurrencyKey,
        String dedupeKey
) {
    public BackgroundJobCreate {
        if (jobType == null || jobType.isBlank()) {
            throw new IllegalArgumentException("jobType is required");
        }
        payloadJson = payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson;
        maxAttempts = Math.max(1, maxAttempts);
        if (concurrencyKey == null || concurrencyKey.isBlank()) {
            throw new IllegalArgumentException("concurrencyKey is required");
        }
    }
}
