package com.pixierge.api.background;

import java.time.OffsetDateTime;

record BackgroundFileActivityRow(
        String path,
        String result,
        OffsetDateTime observedAt,
        String message
) {
}
