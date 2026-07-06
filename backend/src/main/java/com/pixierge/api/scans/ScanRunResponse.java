package com.pixierge.api.scans;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ScanRunResponse(
        UUID id,
        UUID libraryId,
        UUID rootId,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        int scannedFileCount,
        int addedCount,
        int unchangedCount,
        int movedCount,
        int modifiedCount,
        int duplicateCount,
        int missingCount,
        int reappearedCount,
        int errorCount,
        List<ScanErrorResponse> errors
) {
}
