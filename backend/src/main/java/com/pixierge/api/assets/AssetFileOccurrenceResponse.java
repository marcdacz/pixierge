package com.pixierge.api.assets;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AssetFileOccurrenceResponse(
        UUID id,
        UUID libraryId,
        String libraryName,
        String path,
        String folderPath,
        String fileName,
        long sizeBytes,
        OffsetDateTime modifiedAt,
        String status
) {
}
