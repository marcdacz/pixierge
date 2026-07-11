package com.pixierge.api.albums;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AlbumSummaryResponse(
        UUID id,
        String name,
        UUID coverAssetId,
        String coverFileName,
        String kind,
        int itemCount,
        int sourceLibraryCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
