package com.pixierge.api.scans;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ScanIdentityJobPayload(
        UUID scanRunId,
        UUID libraryId,
        UUID rootId,
        UUID assetFileId,
        String path,
        String normalizedPath,
        String fileName,
        Long size,
        OffsetDateTime modifiedAt,
        List<ScanIdentityJobItem> items,
        String subtreePath
) {
    public record ScanIdentityJobItem(
            UUID rootId,
            UUID assetFileId,
            String path,
            String normalizedPath,
            String fileName,
            long size,
            OffsetDateTime modifiedAt
    ) {
    }

    public ScanIdentityJobPayload(
            UUID scanRunId,
            UUID libraryId,
            UUID rootId,
            List<ScanIdentityJobItem> items,
            String subtreePath
    ) {
        this(scanRunId, libraryId, rootId, null, null, null, null, null, null, items, subtreePath);
    }

    public ScanIdentityJobPayload(
            UUID scanRunId,
            UUID libraryId,
            UUID rootId,
            UUID assetFileId,
            String path,
            String normalizedPath,
            String fileName,
            long size,
            OffsetDateTime modifiedAt
    ) {
        this(scanRunId, libraryId, rootId, assetFileId, path, normalizedPath, fileName, size, modifiedAt, null);
    }

    public ScanIdentityJobPayload(
            UUID scanRunId,
            UUID libraryId,
            UUID rootId,
            UUID assetFileId,
            String path,
            String normalizedPath,
            String fileName,
            long size,
            OffsetDateTime modifiedAt,
            String subtreePath
    ) {
        this(scanRunId, libraryId, rootId, assetFileId, path, normalizedPath, fileName, size, modifiedAt, null, subtreePath);
    }

    public List<ScanIdentityJobItem> identityItems() {
        if (items != null && !items.isEmpty()) {
            return items;
        }
        if (rootId == null || assetFileId == null || path == null || normalizedPath == null || fileName == null
                || size == null || modifiedAt == null) {
            return List.of();
        }
        return List.of(new ScanIdentityJobItem(rootId, assetFileId, path, normalizedPath, fileName, size, modifiedAt));
    }

    public UUID completionRootId() {
        if (rootId != null) {
            return rootId;
        }
        List<ScanIdentityJobItem> identityItems = identityItems();
        if (identityItems.isEmpty()) {
            return null;
        }
        UUID firstRootId = identityItems.getFirst().rootId();
        return identityItems.stream().allMatch(item -> item.rootId().equals(firstRootId)) ? firstRootId : null;
    }
}
