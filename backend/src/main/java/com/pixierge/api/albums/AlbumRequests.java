package com.pixierge.api.albums;

import java.util.List;
import java.util.UUID;

record CreateAlbumRequest(String name) {}

record UpdateAlbumRequest(String name, UUID coverAssetId) {}

record AddAlbumItemsRequest(List<UUID> albumIds, List<AlbumAssetItemRequest> items) {}

record AlbumAssetItemRequest(UUID assetId, UUID sourceLibraryId) {}

record DeleteAlbumItemsRequest(List<UUID> assetIds) {}

record UpsertAlbumMemberRequest(UUID userId, String role) {}

record SetAssetPrivacyRequest(UUID libraryId, List<UUID> assetIds, boolean privateItems) {}

record ApprovePrivateItemsRequest(
    UUID recipientUserId, UUID sourceLibraryId, List<UUID> assetIds) {}
