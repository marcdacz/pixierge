package com.pixierge.api.tags;

import java.util.List;
import java.util.UUID;

record CreateTagRequest(String name) {
}

record UpdateTagRequest(String name) {
}

record AssignAssetTagsRequest(List<UUID> tagIds, List<AssetItemRequest> items) {
}

record DeleteAssetTagsRequest(List<UUID> tagIds, List<UUID> assetIds) {
}

record AssetItemRequest(UUID assetId, UUID sourceLibraryId) {
}
