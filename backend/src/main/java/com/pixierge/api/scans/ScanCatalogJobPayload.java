package com.pixierge.api.scans;

import java.util.UUID;

public record ScanCatalogJobPayload(
        UUID scanRunId,
        UUID libraryId,
        UUID rootId
) {
}
