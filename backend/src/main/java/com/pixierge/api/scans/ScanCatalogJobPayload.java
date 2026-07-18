package com.pixierge.api.scans;

import java.util.UUID;

public record ScanCatalogJobPayload(
        UUID scanRunId,
        UUID libraryId,
        UUID rootId,
        String subtreePath
) {
    public ScanCatalogJobPayload(UUID scanRunId, UUID libraryId, UUID rootId) {
        this(scanRunId, libraryId, rootId, null);
    }
}
