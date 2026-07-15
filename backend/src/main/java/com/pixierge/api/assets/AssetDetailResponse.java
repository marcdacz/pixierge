package com.pixierge.api.assets;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AssetDetailResponse(
        UUID id,
        String contentHash,
        String identityStatus,
        String mediaType,
        String availability,
        int duplicateCount,
        Metadata metadata,
        List<FileOccurrence> files,
        List<Tag> tags
) {
    public record Metadata(
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

    public record FileOccurrence(
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

    public record Tag(UUID id, String name) {
    }
}
