package com.pixierge.api.background;

import java.time.OffsetDateTime;
import java.util.UUID;

record BackgroundFileActivityRow(
        UUID assetId,
        String path,
        String result,
        OffsetDateTime observedAt,
        String message
) {
}
