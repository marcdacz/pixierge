package com.pixierge.api.assets;

import java.util.List;
import java.util.UUID;

public record LibraryTreeNodeResponse(
        String id,
        UUID libraryId,
        String libraryName,
        String path,
        String name,
        int assetCount,
        int childCount,
        List<LibraryTreeNodeResponse> children
) {
}
