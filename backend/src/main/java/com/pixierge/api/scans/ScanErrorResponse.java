package com.pixierge.api.scans;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ScanErrorResponse(
        UUID id,
        String path,
        String errorCode,
        String message,
        OffsetDateTime createdAt
) {
}
