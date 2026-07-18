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
        OffsetDateTime modifiedAt
) {
}
