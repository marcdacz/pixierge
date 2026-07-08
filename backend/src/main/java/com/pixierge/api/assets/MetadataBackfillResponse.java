package com.pixierge.api.assets;

public record MetadataBackfillResponse(
        int processedCount,
        int failedCount
) {
}
