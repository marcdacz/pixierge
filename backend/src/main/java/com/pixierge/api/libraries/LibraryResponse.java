package com.pixierge.api.libraries;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record LibraryResponse(
        UUID id,
        String name,
        long sourceCount,
        long availableSourceCount,
        long unavailableSourceCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<LibrarySourceResponse> sources
) {
}
