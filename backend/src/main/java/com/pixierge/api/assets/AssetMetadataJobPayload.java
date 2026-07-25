package com.pixierge.api.assets;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AssetMetadataJobPayload(
        UUID assetId,
        UUID assetFileId,
        String normalizedPath,
        String fileName,
        long sizeBytes,
        OffsetDateTime modifiedAt,
        String mediaType
) {
}
