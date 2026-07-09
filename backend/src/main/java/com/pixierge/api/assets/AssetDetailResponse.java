package com.pixierge.api.assets;

import java.util.List;
import java.util.UUID;

public record AssetDetailResponse(
        UUID id,
        String contentHash,
        String identityStatus,
        String mediaType,
        String availability,
        int duplicateCount,
        AssetMetadataResponse metadata,
        List<AssetFileOccurrenceResponse> files
) {
}
