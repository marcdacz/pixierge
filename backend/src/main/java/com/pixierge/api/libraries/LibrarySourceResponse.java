package com.pixierge.api.libraries;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LibrarySourceResponse(
        UUID id,
        String path,
        boolean available,
        String unavailableReason,
        OffsetDateTime createdAt
) {
}
