package com.pixierge.api.assets;

import java.util.List;

public record AssetBrowseResponse(
        List<Section> sections,
        int totalCount,
        int page,
        int pageSize,
        boolean hasNext
) {
    public record Section(
            String folderPath,
            String folderName,
            List<AssetSummaryResponse> assets
    ) {
    }
}
