package com.pixierge.api.assets;

import java.util.List;

public record AssetSectionResponse(
        String folderPath,
        String folderName,
        List<AssetSummaryResponse> assets
) {
}
