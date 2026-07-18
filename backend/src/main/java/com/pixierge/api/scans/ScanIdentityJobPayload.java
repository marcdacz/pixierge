package com.pixierge.api.scans;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ScanIdentityJobPayload(
        UUID scanRunId,
        UUID libraryId,
        UUID rootId,
        UUID assetFileId,
        String path,
        String normalizedPath,
        String fileName,
        long size,
        OffsetDateTime modifiedAt,
        String subtreePath
) {
    public ScanIdentityJobPayload(
            UUID scanRunId,
            UUID libraryId,
            UUID rootId,
            UUID assetFileId,
            String path,
            String normalizedPath,
            String fileName,
            long size,
            OffsetDateTime modifiedAt
    ) {
        this(scanRunId, libraryId, rootId, assetFileId, path, normalizedPath, fileName, size, modifiedAt, null);
    }
}
