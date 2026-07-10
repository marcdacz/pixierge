package com.pixierge.api.assets;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AssetSummaryResponse(
        UUID id,
        String fileName,
        String displayPath,
        String folderPath,
        UUID libraryId,
        String libraryName,
        String availability,
        String identityStatus,
        int duplicateCount,
        OffsetDateTime capturedAt,
        OffsetDateTime observedAt,
        String mediaType,
        String mimeType,
        Integer width,
        Integer height,
        boolean previewable,
        String thumbnailStatus,
        String thumbnailCacheKey,
        String thumbnailPlaceholder
) {
}
