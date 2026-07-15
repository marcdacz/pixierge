package com.pixierge.api.assets;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record LibraryTreeResponse(
        List<Node> roots,
        Map<UUID, Integer> libraryRootAssetCounts,
        Map<UUID, Integer> libraryAssetCounts
) {
    public record Node(
            String id,
            UUID libraryId,
            String libraryName,
            String path,
            String name,
            int assetCount,
            int childCount,
            List<Node> children
    ) {
    }
}
