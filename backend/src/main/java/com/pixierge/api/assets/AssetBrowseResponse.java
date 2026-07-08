package com.pixierge.api.assets;

import java.util.List;

public record AssetBrowseResponse(
        List<AssetSectionResponse> sections,
        int totalCount,
        int page,
        int pageSize,
        boolean hasNext
) {
}
