package com.pixierge.api.background;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BackgroundFileActivitySummary(
        UUID assetId,
        String path,
        String fileName,
        String status,
        UUID jobId,
        String batchLabel,
        OffsetDateTime updatedAt,
        String message,
        Long durationMs
) {
    public BackgroundFileActivitySummary(
            UUID assetId, String path, String fileName, String status, UUID jobId, String batchLabel,
            OffsetDateTime updatedAt, String message
    ) {
        this(assetId, path, fileName, status, jobId, batchLabel, updatedAt, message, null);
    }
}
