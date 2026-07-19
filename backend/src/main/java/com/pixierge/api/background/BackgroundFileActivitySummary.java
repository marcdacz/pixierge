package com.pixierge.api.background;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BackgroundFileActivitySummary(
        String path,
        String fileName,
        String status,
        UUID jobId,
        String batchLabel,
        OffsetDateTime updatedAt,
        String message
) {
}
