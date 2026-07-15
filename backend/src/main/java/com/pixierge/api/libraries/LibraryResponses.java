package com.pixierge.api.libraries;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

record LibraryResponse(
        UUID id,
        String name,
        String status,
        long sourceCount,
        long availableSourceCount,
        long unavailableSourceCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime archivedAt,
        List<LibrarySourceResponse> sources,
        List<LibraryExclusionPatternResponse> exclusionPatterns
) {
}

record LibrarySourceResponse(
        UUID id,
        String path,
        boolean available,
        String unavailableReason,
        OffsetDateTime createdAt
) {
}

record LibraryExclusionPatternResponse(
        UUID id,
        String pattern,
        OffsetDateTime createdAt
) {
}

record RenameFolderResponse(String path, String name) {
}
