package com.pixierge.api.albums;

import java.util.List;
import java.util.UUID;

record CreateAlbumRequest(String name) {
}

record UpdateAlbumRequest(String name, UUID coverAssetId) {
}

record AddAlbumItemsRequest(List<UUID> albumIds, List<AlbumAssetItemRequest> items) {
}

record AlbumAssetItemRequest(UUID assetId, UUID sourceLibraryId) {
}

record DeleteAlbumItemsRequest(List<UUID> assetIds) {
}
