package com.pixierge.api.assets;

import com.pixierge.api.albums.AlbumKind;
import com.pixierge.api.db.QAlbumItems;
import com.pixierge.api.db.QAlbums;
import com.pixierge.api.db.QAssetFiles;
import com.pixierge.api.db.QAssetMetadata;
import com.pixierge.api.db.QAssetTags;
import com.pixierge.api.db.QAssets;
import com.pixierge.api.db.QLibraries;
import com.pixierge.api.db.QLibraryMembers;
import com.pixierge.api.db.QLibraryRoots;
import com.pixierge.api.db.QPasswordCredentials;
import com.pixierge.api.db.QSearchDocuments;
import com.pixierge.api.db.QSessions;
import com.pixierge.api.db.QTags;
import com.pixierge.api.db.QThumbnails;
import com.pixierge.api.db.QUserRoles;
import com.pixierge.api.db.QUsers;
import com.pixierge.api.search.SearchClause;
import com.pixierge.api.search.SearchField;
import com.pixierge.api.search.SearchQuery;
import com.querydsl.sql.SQLQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "pixierge.scheduler.enabled=false")
class AssetRepositoryIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000402");
    private static final UUID FAMILY_LIBRARY_ID = UUID.fromString("00000000-0000-0000-0000-000000000403");
    private static final UUID TRAVEL_LIBRARY_ID = UUID.fromString("00000000-0000-0000-0000-000000000404");
    private static final UUID PRIVATE_LIBRARY_ID = UUID.fromString("00000000-0000-0000-0000-000000000405");
    private static final UUID FAMILY_ROOT_ID = UUID.fromString("00000000-0000-0000-0000-000000000406");
    private static final UUID TRAVEL_ROOT_ID = UUID.fromString("00000000-0000-0000-0000-000000000407");
    private static final UUID PRIVATE_ROOT_ID = UUID.fromString("00000000-0000-0000-0000-000000000408");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-13T00:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AssetRepository repository;
    @Autowired
    private SQLQueryFactory queryFactory;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void clearData() {
        transactionTemplate.executeWithoutResult(status -> {
            queryFactory.delete(QThumbnails.thumbnails).execute();
            queryFactory.delete(QSearchDocuments.searchDocuments).execute();
            queryFactory.delete(QAssetMetadata.assetMetadata).execute();
            queryFactory.delete(QAssetFiles.assetFiles).execute();
            queryFactory.delete(QAlbumItems.albumItems).execute();
            queryFactory.delete(QAssetTags.assetTags).execute();
            queryFactory.delete(QAlbums.albums).execute();
            queryFactory.delete(QTags.tags).execute();
            queryFactory.delete(QAssets.assets).execute();
            queryFactory.delete(QLibraryMembers.libraryMembers).execute();
            queryFactory.delete(QLibraryRoots.libraryRoots).execute();
            queryFactory.delete(QLibraries.libraries).execute();
            queryFactory.delete(QSessions.sessions).execute();
            queryFactory.delete(QUserRoles.userRoles).execute();
            queryFactory.delete(QPasswordCredentials.passwordCredentials).execute();
            queryFactory.delete(QUsers.users).execute();
        });
    }

    @Test
    void browseTagAssetsUsesAssignmentSourceLibraryAsTheDisplayContext() {
        UUID assetId = UUID.fromString("00000000-0000-0000-0000-000000000409");
        UUID tagId = UUID.fromString("00000000-0000-0000-0000-000000000410");
        transactionTemplate.executeWithoutResult(status -> {
            user(USER_ID, "owner");
            library(FAMILY_LIBRARY_ID, FAMILY_ROOT_ID, "Family", "/photos/family", USER_ID);
            library(TRAVEL_LIBRARY_ID, TRAVEL_ROOT_ID, "Travel", "/photos/travel", USER_ID);
            asset(assetId, "shared-hash", "image/jpeg", 2);
            assetFile(assetId, FAMILY_LIBRARY_ID, FAMILY_ROOT_ID, "/photos/family/original.jpg", "original.jpg", "active");
            assetFile(assetId, TRAVEL_LIBRARY_ID, TRAVEL_ROOT_ID, "/photos/travel/album-copy.jpg", "album-copy.jpg", "active");
            metadata(assetId, "jpg", "image/jpeg", OffsetDateTime.parse("2026-03-01T00:00:00Z"), "Canon", "R5");
            tag(tagId, USER_ID, "Travel");
            assetTag(tagId, assetId, TRAVEL_LIBRARY_ID, USER_ID);
        });

        AssetRepository.BrowseRows rows = transactionTemplate.execute(status ->
                repository.browseTagAssets(USER_ID, false, tagId, 0, 10));

        assertThat(rows.totalCount()).isEqualTo(1);
        assertThat(rows.assets()).singleElement().satisfies(asset -> {
            assertThat(asset.assetId()).isEqualTo(assetId);
            assertThat(asset.libraryId()).isEqualTo(TRAVEL_LIBRARY_ID);
            assertThat(asset.fileName()).isEqualTo("album-copy.jpg");
            assertThat(asset.duplicateCount()).isEqualTo(2);
        });
    }

    @Test
    void structuredSearchCombinesFolderDatesStarredAndCameraPredicates() {
        UUID matchedAsset = UUID.fromString("00000000-0000-0000-0000-000000000411");
        UUID wrongFolderAsset = UUID.fromString("00000000-0000-0000-0000-000000000412");
        UUID starredAlbum = UUID.fromString("00000000-0000-0000-0000-000000000413");
        transactionTemplate.executeWithoutResult(status -> {
            user(USER_ID, "owner");
            library(FAMILY_LIBRARY_ID, FAMILY_ROOT_ID, "Family", "/photos", USER_ID);
            asset(matchedAsset, "matched-hash", "image/jpeg", 1);
            assetFile(matchedAsset, FAMILY_LIBRARY_ID, FAMILY_ROOT_ID, "/photos/Trips/Japan/canon.jpg", "canon.jpg", "active");
            metadata(matchedAsset, "jpg", "image/jpeg", OffsetDateTime.parse("2026-04-01T10:00:00Z"), "Canon", "R5");
            asset(wrongFolderAsset, "wrong-folder-hash", "image/jpeg", 1);
            assetFile(wrongFolderAsset, FAMILY_LIBRARY_ID, FAMILY_ROOT_ID, "/photos/Home/canon.jpg", "canon.jpg", "active");
            metadata(wrongFolderAsset, "jpg", "image/jpeg", OffsetDateTime.parse("2026-04-01T10:00:00Z"), "Canon", "R5");
            album(starredAlbum, USER_ID, "Starred", AlbumKind.STARRED);
            albumItem(starredAlbum, matchedAsset, FAMILY_LIBRARY_ID, 1);
            albumItem(starredAlbum, wrongFolderAsset, FAMILY_LIBRARY_ID, 2);
        });
        SearchQuery query = new SearchQuery("", "", List.of(
                clause(SearchField.FOLDER, "/photos/Trips/"),
                clause(SearchField.AFTER, "2026-01-01"),
                clause(SearchField.BEFORE, "2026-12-31"),
                clause(SearchField.IS, "starred"),
                clause(SearchField.CAMERA, "canon")
        ));

        AssetRepository.BrowseRows rows = transactionTemplate.execute(status -> repository.browse(
                USER_ID,
                false,
                criteria(query, null, null, null, false)
        ));

        assertThat(rows.totalCount()).isEqualTo(1);
        assertThat(rows.assets()).singleElement().satisfies(asset ->
                assertThat(asset.assetId()).isEqualTo(matchedAsset));
    }

    @Test
    void browseFiltersByAllRequestedTagsAndUnavailableFolderReturnsEmptyPageWithTotalZero() {
        UUID firstAsset = UUID.fromString("00000000-0000-0000-0000-000000000414");
        UUID secondAsset = UUID.fromString("00000000-0000-0000-0000-000000000415");
        UUID familyTag = UUID.fromString("00000000-0000-0000-0000-000000000416");
        UUID travelTag = UUID.fromString("00000000-0000-0000-0000-000000000417");
        transactionTemplate.executeWithoutResult(status -> {
            user(USER_ID, "owner");
            library(FAMILY_LIBRARY_ID, FAMILY_ROOT_ID, "Family", "/photos", USER_ID);
            asset(firstAsset, "first-hash", "image/jpeg", 1);
            assetFile(firstAsset, FAMILY_LIBRARY_ID, FAMILY_ROOT_ID, "/photos/Trips/first.jpg", "first.jpg", "active");
            metadata(firstAsset, "jpg", "image/jpeg", OffsetDateTime.parse("2026-05-01T00:00:00Z"), null, null);
            asset(secondAsset, "second-hash", "image/jpeg", 1);
            assetFile(secondAsset, FAMILY_LIBRARY_ID, FAMILY_ROOT_ID, "/photos/Trips/second.jpg", "second.jpg", "active");
            metadata(secondAsset, "jpg", "image/jpeg", OffsetDateTime.parse("2026-05-02T00:00:00Z"), null, null);
            tag(familyTag, USER_ID, "Family");
            tag(travelTag, USER_ID, "Travel");
            assetTag(familyTag, firstAsset, FAMILY_LIBRARY_ID, USER_ID);
            assetTag(travelTag, firstAsset, FAMILY_LIBRARY_ID, USER_ID);
            assetTag(familyTag, secondAsset, FAMILY_LIBRARY_ID, USER_ID);
        });

        AssetRepository.BrowseRows taggedRows = transactionTemplate.execute(status -> repository.browse(
                USER_ID,
                false,
                criteria(SearchQuery.empty(), null, null, List.of(familyTag, travelTag), false)
        ));
        AssetRepository.BrowseRows missingFolderRows = transactionTemplate.execute(status -> repository.browse(
                USER_ID,
                false,
                criteria(SearchQuery.empty(), "/photos/Missing", null, null, false)
        ));

        assertThat(taggedRows.totalCount()).isEqualTo(1);
        assertThat(taggedRows.assets()).singleElement().satisfies(asset ->
                assertThat(asset.assetId()).isEqualTo(firstAsset));
        assertThat(missingFolderRows.totalCount()).isZero();
        assertThat(missingFolderRows.assets()).isEmpty();
    }

    private AssetRepository.AssetSearchCriteria criteria(
            SearchQuery query,
            String folder,
            String availability,
            List<UUID> tagIds,
            boolean duplicatesOnly
    ) {
        return new AssetRepository.AssetSearchCriteria(
                null,
                folder,
                false,
                query,
                availability,
                null,
                duplicatesOnly,
                tagIds,
                USER_ID,
                0,
                20
        );
    }

    private SearchClause clause(SearchField field, String value) {
        return new SearchClause(field, value, false, 0, value.length());
    }

    private void user(UUID userId, String username) {
        QUsers users = QUsers.users;
        queryFactory.insert(users)
                .set(users.id, userId)
                .set(users.username, username)
                .set(users.status, "active")
                .set(users.createdAt, NOW)
                .set(users.updatedAt, NOW)
                .execute();
    }

    private void library(UUID libraryId, UUID rootId, String name, String path, UUID memberUserId) {
        QLibraries libraries = QLibraries.libraries;
        queryFactory.insert(libraries)
                .set(libraries.id, libraryId)
                .set(libraries.name, name)
                .set(libraries.status, "active")
                .set(libraries.createdBy, memberUserId)
                .set(libraries.createdAt, NOW)
                .set(libraries.updatedAt, NOW)
                .execute();
        queryFactory.insert(QLibraryRoots.libraryRoots)
                .set(QLibraryRoots.libraryRoots.id, rootId)
                .set(QLibraryRoots.libraryRoots.libraryId, libraryId)
                .set(QLibraryRoots.libraryRoots.path, path)
                .set(QLibraryRoots.libraryRoots.normalizedPath, path)
                .set(QLibraryRoots.libraryRoots.createdAt, NOW)
                .set(QLibraryRoots.libraryRoots.updatedAt, NOW)
                .execute();
        queryFactory.insert(QLibraryMembers.libraryMembers)
                .set(QLibraryMembers.libraryMembers.libraryId, libraryId)
                .set(QLibraryMembers.libraryMembers.userId, memberUserId)
                .set(QLibraryMembers.libraryMembers.memberRole, "owner")
                .set(QLibraryMembers.libraryMembers.createdAt, NOW)
                .execute();
    }

    private void asset(UUID assetId, String contentHash, String mediaType, int availableFileCount) {
        QAssets assets = QAssets.assets;
        queryFactory.insert(assets)
                .set(assets.id, assetId)
                .set(assets.contentHash, contentHash)
                .set(assets.mediaType, mediaType)
                .set(assets.availableFileCount, availableFileCount)
                .set(assets.firstObservedAt, NOW)
                .set(assets.lastObservedAt, NOW)
                .execute();
    }

    private void assetFile(UUID assetId, UUID libraryId, UUID rootId, String path, String fileName, String status) {
        QAssetFiles files = QAssetFiles.assetFiles;
        String contentHash = queryFactory.select(QAssets.assets.contentHash)
                .from(QAssets.assets)
                .where(QAssets.assets.id.eq(assetId))
                .fetchOne();
        queryFactory.insert(files)
                .set(files.id, UUID.nameUUIDFromBytes((assetId + path).getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .set(files.assetId, assetId)
                .set(files.libraryId, libraryId)
                .set(files.rootId, rootId)
                .set(files.path, path)
                .set(files.normalizedPath, path)
                .set(files.fileName, fileName)
                .set(files.sizeBytes, 100L)
                .set(files.modifiedAt, NOW)
                .set(files.contentHash, contentHash)
                .set(files.status, status)
                .set(files.firstObservedAt, NOW)
                .set(files.lastObservedAt, NOW)
                .execute();
    }

    private void metadata(
            UUID assetId,
            String fileExtension,
            String mimeType,
            OffsetDateTime capturedAt,
            String cameraMake,
            String cameraModel
    ) {
        QAssetMetadata metadata = QAssetMetadata.assetMetadata;
        queryFactory.insert(metadata)
                .set(metadata.assetId, assetId)
                .set(metadata.capturedAt, capturedAt)
                .set(metadata.width, 100)
                .set(metadata.height, 100)
                .set(metadata.fileExtension, fileExtension)
                .set(metadata.mimeType, mimeType)
                .set(metadata.cameraMake, cameraMake)
                .set(metadata.cameraModel, cameraModel)
                .set(metadata.sourceVersion, "test")
                .set(metadata.extractionStatus, "extracted")
                .set(metadata.extractedAt, NOW)
                .execute();
    }

    private void album(UUID albumId, UUID ownerUserId, String name, String kind) {
        QAlbums albums = QAlbums.albums;
        queryFactory.insert(albums)
                .set(albums.id, albumId)
                .set(albums.ownerUserId, ownerUserId)
                .set(albums.name, name)
                .set(albums.kind, kind)
                .set(albums.createdAt, NOW)
                .set(albums.updatedAt, NOW)
                .execute();
    }

    private void albumItem(UUID albumId, UUID assetId, UUID sourceLibraryId, int position) {
        QAlbumItems items = QAlbumItems.albumItems;
        queryFactory.insert(items)
                .set(items.albumId, albumId)
                .set(items.assetId, assetId)
                .set(items.sourceLibraryId, sourceLibraryId)
                .set(items.position, position)
                .set(items.addedBy, USER_ID)
                .set(items.createdAt, NOW)
                .execute();
    }

    private void tag(UUID tagId, UUID ownerUserId, String name) {
        QTags tags = QTags.tags;
        queryFactory.insert(tags)
                .set(tags.id, tagId)
                .set(tags.ownerUserId, ownerUserId)
                .set(tags.name, name)
                .set(tags.normalizedName, name.toLowerCase(java.util.Locale.ROOT))
                .set(tags.createdBy, ownerUserId)
                .set(tags.createdAt, NOW)
                .set(tags.updatedAt, NOW)
                .execute();
    }

    private void assetTag(UUID tagId, UUID assetId, UUID sourceLibraryId, UUID addedBy) {
        QAssetTags tags = QAssetTags.assetTags;
        queryFactory.insert(tags)
                .set(tags.tagId, tagId)
                .set(tags.assetId, assetId)
                .set(tags.sourceLibraryId, sourceLibraryId)
                .set(tags.addedBy, addedBy)
                .set(tags.createdAt, NOW)
                .execute();
    }
}
