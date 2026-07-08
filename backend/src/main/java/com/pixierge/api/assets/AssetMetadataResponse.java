package com.pixierge.api.assets;

import java.time.OffsetDateTime;

public record AssetMetadataResponse(
        OffsetDateTime capturedAt,
        Integer width,
        Integer height,
        String fileExtension,
        String mimeType,
        String extractionStatus,
        OffsetDateTime extractedAt,
        String errorMessage
) {
}
