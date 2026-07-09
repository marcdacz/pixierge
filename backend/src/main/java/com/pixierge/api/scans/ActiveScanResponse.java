package com.pixierge.api.scans;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ActiveScanResponse(
        UUID id,
        UUID libraryId,
        String libraryName,
        UUID rootId,
        String rootPath,
        String status,
        OffsetDateTime startedAt,
        int scannedFileCount,
        int addedCount,
        int unchangedCount,
        int movedCount,
        int modifiedCount,
        int duplicateCount,
        int missingCount,
        int reappearedCount,
        int errorCount
) {
}
