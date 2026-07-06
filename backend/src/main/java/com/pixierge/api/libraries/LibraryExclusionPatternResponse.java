package com.pixierge.api.libraries;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LibraryExclusionPatternResponse(
        UUID id,
        String pattern,
        OffsetDateTime createdAt
) {
}
