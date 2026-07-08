package com.pixierge.api.assets;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record LibraryTreeResponse(
        List<LibraryTreeNodeResponse> roots,
        Map<UUID, Integer> libraryRootAssetCounts
) {
}
