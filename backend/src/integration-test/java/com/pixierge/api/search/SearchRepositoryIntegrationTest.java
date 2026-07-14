package com.pixierge.api.search;

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
import com.pixierge.api.db.QUserRoles;
import com.pixierge.api.db.QUsers;
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
import static org.assertj.core.api.Assertions.tuple;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "pixierge.scheduler.enabled=false")
class SearchRepositoryIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID FAMILY_LIBRARY_ID = UUID.fromString("00000000-0000-0000-0000-000000000203");
    private static final UUID PRIVATE_LIBRARY_ID = UUID.fromString("00000000-0000-0000-0000-000000000204");
    private static final UUID ARCHIVED_LIBRARY_ID = UUID.fromString("00000000-0000-0000-0000-000000000205");
    private static final UUID FAMILY_ROOT_ID = UUID.fromString("00000000-0000-0000-0000-000000000206");
    private static final UUID PRIVATE_ROOT_ID = UUID.fromString("00000000-0000-0000-0000-000000000207");
    private static final UUID ARCHIVED_ROOT_ID = UUID.fromString("00000000-0000-0000-0000-000000000208");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-13T00:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private SearchRepository repository;
    @Autowired
    private SQLQueryFactory queryFactory;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void clearData() {
        transactionTemplate.executeWithoutResult(status -> {
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
    void librarySuggestionsRespectMembershipActiveStatusAndCommaPrefix() {
        transactionTemplate.executeWithoutResult(status -> {
            user(USER_ID, "owner");
            user(OTHER_USER_ID, "other");
            library(FAMILY_LIBRARY_ID, "Family Photos", "active", USER_ID);
            library(PRIVATE_LIBRARY_ID, "Private Photos", "active", OTHER_USER_ID);
            library(ARCHIVED_LIBRARY_ID, "Family Archive", "archived", USER_ID);
        });

        List<SearchSuggestionResponse> memberSuggestions =
                repository.suggest(SearchField.LIBRARY, "selected, fam", 10, USER_ID, false);
        List<SearchSuggestionResponse> adminSuggestions =
                repository.suggest(SearchField.LIBRARY, "photos", 10, USER_ID, true);

        assertThat(memberSuggestions)
                .extracting(SearchSuggestionResponse::value, SearchSuggestionResponse::label)
                .containsExactly(tuple("selected,\"Family Photos\"", "Family Photos"));
        assertThat(adminSuggestions)
                .extracting(SearchSuggestionResponse::label)
                .containsExactly("Family Photos", "Private Photos");
    }

    @Test
    void albumAndTagSuggestionsAreOwnerScopedAndQuoteCommaContinuation() {
        transactionTemplate.executeWithoutResult(status -> {
            user(USER_ID, "owner");
            user(OTHER_USER_ID, "other");
            album(USER_ID, "Japan 2025");
            album(OTHER_USER_ID, "Japan Private");
            tag(USER_ID, "Family Travel");
            tag(OTHER_USER_ID, "Family Private");
        });

        List<SearchSuggestionResponse> albums =
                repository.suggest(SearchField.ALBUM, "picked, jap", 10, USER_ID, false);
        List<SearchSuggestionResponse> tags =
                repository.suggest(SearchField.TAG, "fam", 10, USER_ID, false);

        assertThat(albums)
                .extracting(SearchSuggestionResponse::value, SearchSuggestionResponse::label)
                .containsExactly(tuple("picked,\"Japan 2025\"", "Japan 2025"));
        assertThat(tags)
                .extracting(SearchSuggestionResponse::value, SearchSuggestionResponse::label)
                .containsExactly(tuple("\"Family Travel\"", "Family Travel"));
    }

    @Test
    void folderSuggestionsExtractDistinctReadableFoldersAndQuoteWhitespace() {
        transactionTemplate.executeWithoutResult(status -> {
            user(USER_ID, "owner");
            user(OTHER_USER_ID, "other");
            library(FAMILY_LIBRARY_ID, "Family Photos", "active", USER_ID);
            library(PRIVATE_LIBRARY_ID, "Private Photos", "active", OTHER_USER_ID);
            root(FAMILY_ROOT_ID, FAMILY_LIBRARY_ID, "/photos");
            root(PRIVATE_ROOT_ID, PRIVATE_LIBRARY_ID, "/private");
            assetFile(asset("hash-family-events"), FAMILY_LIBRARY_ID, FAMILY_ROOT_ID,
                    "/photos/Family Events/birthday.jpg", "birthday.jpg", "jpg", null, null, "active");
            assetFile(asset("hash-japan-one"), FAMILY_LIBRARY_ID, FAMILY_ROOT_ID,
                    "/photos/Trips/Japan/photo.jpg", "photo.jpg", "jpg", null, null, "active");
            assetFile(asset("hash-japan-two"), FAMILY_LIBRARY_ID, FAMILY_ROOT_ID,
                    "/photos/Trips/Japan/video.mp4", "video.mp4", "mp4", null, null, "active");
            assetFile(asset("hash-private"), PRIVATE_LIBRARY_ID, PRIVATE_ROOT_ID,
                    "/private/Secret/photo.jpg", "photo.jpg", "jpg", null, null, "active");
        });

        List<SearchSuggestionResponse> suggestions =
                repository.suggest(SearchField.FOLDER, "", 10, USER_ID, false);

        assertThat(suggestions)
                .extracting(SearchSuggestionResponse::value, SearchSuggestionResponse::label)
                .containsExactly(
                        tuple("\"/photos/Family Events\"", "/photos/Family Events"),
                        tuple("/photos/Trips/Japan", "/photos/Trips/Japan")
                );
    }

    @Test
    void extensionSuggestionsUseDottedValuesAndPreserveCompletedCommaPrefix() {
        transactionTemplate.executeWithoutResult(status -> {
            user(USER_ID, "owner");
            library(FAMILY_LIBRARY_ID, "Family Photos", "active", USER_ID);
            root(FAMILY_ROOT_ID, FAMILY_LIBRARY_ID, "/photos");
            assetFile(asset("hash-jpg"), FAMILY_LIBRARY_ID, FAMILY_ROOT_ID,
                    "/photos/photo.jpg", "photo.jpg", "jpg", null, null, "active");
            assetFile(asset("hash-heic"), FAMILY_LIBRARY_ID, FAMILY_ROOT_ID,
                    "/photos/photo.heic", "photo.heic", "heic", null, null, "active");
        });

        List<SearchSuggestionResponse> suggestions =
                repository.suggest(SearchField.EXTENSION, "jpg, h", 10, USER_ID, false);

        assertThat(suggestions)
                .extracting(SearchSuggestionResponse::value, SearchSuggestionResponse::label)
                .containsExactly(tuple("jpg,.heic", "jpg,.heic"));
    }

    @Test
    void cameraSuggestionsCombineMetadataAndFilterToReadableActiveLibraries() {
        transactionTemplate.executeWithoutResult(status -> {
            user(USER_ID, "owner");
            user(OTHER_USER_ID, "other");
            library(FAMILY_LIBRARY_ID, "Family Photos", "active", USER_ID);
            library(PRIVATE_LIBRARY_ID, "Private Photos", "active", OTHER_USER_ID);
            library(ARCHIVED_LIBRARY_ID, "Archived Photos", "archived", USER_ID);
            root(FAMILY_ROOT_ID, FAMILY_LIBRARY_ID, "/photos");
            root(PRIVATE_ROOT_ID, PRIVATE_LIBRARY_ID, "/private");
            root(ARCHIVED_ROOT_ID, ARCHIVED_LIBRARY_ID, "/archive");
            assetFile(asset("hash-canon"), FAMILY_LIBRARY_ID, FAMILY_ROOT_ID,
                    "/photos/canon.jpg", "canon.jpg", "jpg", "Canon", "EOS R5", "active");
            assetFile(asset("hash-sony"), FAMILY_LIBRARY_ID, FAMILY_ROOT_ID,
                    "/photos/sony.jpg", "sony.jpg", "jpg", "Sony", "A7 IV", "missing");
            assetFile(asset("hash-private-canon"), PRIVATE_LIBRARY_ID, PRIVATE_ROOT_ID,
                    "/private/canon.jpg", "canon.jpg", "jpg", "Canon", "Secret", "active");
            assetFile(asset("hash-archived-canon"), ARCHIVED_LIBRARY_ID, ARCHIVED_ROOT_ID,
                    "/archive/canon.jpg", "canon.jpg", "jpg", "Canon", "Archived", "active");
        });

        List<SearchSuggestionResponse> suggestions =
                repository.suggest(SearchField.CAMERA, "canon", 10, USER_ID, false);

        assertThat(suggestions)
                .extracting(SearchSuggestionResponse::value, SearchSuggestionResponse::label)
                .containsExactly(tuple("\"Canon EOS R5\"", "Canon EOS R5"));
    }

    @Test
    void enumSuggestionsFilterByPrefixAndLimitResults() {
        List<SearchSuggestionResponse> suggestions =
                repository.suggest(SearchField.IS, "m", 1, USER_ID, false);

        assertThat(suggestions)
                .extracting(SearchSuggestionResponse::value, SearchSuggestionResponse::label)
                .containsExactly(tuple("missing", "missing"));
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

    private void library(UUID libraryId, String name, String status, UUID memberUserId) {
        QLibraries libraries = QLibraries.libraries;
        queryFactory.insert(libraries)
                .set(libraries.id, libraryId)
                .set(libraries.name, name)
                .set(libraries.status, status)
                .set(libraries.createdBy, memberUserId)
                .set(libraries.createdAt, NOW)
                .set(libraries.updatedAt, NOW)
                .execute();
        queryFactory.insert(QLibraryMembers.libraryMembers)
                .set(QLibraryMembers.libraryMembers.libraryId, libraryId)
                .set(QLibraryMembers.libraryMembers.userId, memberUserId)
                .set(QLibraryMembers.libraryMembers.memberRole, "owner")
                .set(QLibraryMembers.libraryMembers.createdAt, NOW)
                .execute();
    }

    private void root(UUID rootId, UUID libraryId, String path) {
        QLibraryRoots roots = QLibraryRoots.libraryRoots;
        queryFactory.insert(roots)
                .set(roots.id, rootId)
                .set(roots.libraryId, libraryId)
                .set(roots.path, path)
                .set(roots.normalizedPath, path)
                .set(roots.createdAt, NOW)
                .set(roots.updatedAt, NOW)
                .execute();
    }

    private UUID asset(String contentHash) {
        UUID assetId = UUID.nameUUIDFromBytes(contentHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        QAssets assets = QAssets.assets;
        queryFactory.insert(assets)
                .set(assets.id, assetId)
                .set(assets.contentHash, contentHash)
                .set(assets.mediaType, "photo")
                .set(assets.availableFileCount, 1)
                .set(assets.firstObservedAt, NOW)
                .set(assets.lastObservedAt, NOW)
                .execute();
        return assetId;
    }

    private void assetFile(
            UUID assetId,
            UUID libraryId,
            UUID rootId,
            String normalizedPath,
            String fileName,
            String extension,
            String cameraMake,
            String cameraModel,
            String status
    ) {
        QAssetFiles files = QAssetFiles.assetFiles;
        UUID fileId = UUID.nameUUIDFromBytes((assetId + normalizedPath).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        queryFactory.insert(files)
                .set(files.id, fileId)
                .set(files.assetId, assetId)
                .set(files.libraryId, libraryId)
                .set(files.rootId, rootId)
                .set(files.path, normalizedPath)
                .set(files.normalizedPath, normalizedPath)
                .set(files.fileName, fileName)
                .set(files.sizeBytes, 100L)
                .set(files.modifiedAt, NOW)
                .set(files.contentHash, "file-" + assetId)
                .set(files.status, status)
                .set(files.firstObservedAt, NOW)
                .set(files.lastObservedAt, NOW)
                .execute();

        QAssetMetadata metadata = QAssetMetadata.assetMetadata;
        queryFactory.insert(metadata)
                .set(metadata.assetId, assetId)
                .set(metadata.fileExtension, extension)
                .set(metadata.mimeType, "image/" + extension)
                .set(metadata.cameraMake, cameraMake)
                .set(metadata.cameraModel, cameraModel)
                .set(metadata.sourceVersion, "test")
                .set(metadata.extractionStatus, "extracted")
                .set(metadata.extractedAt, NOW)
                .execute();
    }

    private void album(UUID ownerUserId, String name) {
        QAlbums albums = QAlbums.albums;
        queryFactory.insert(albums)
                .set(albums.id, UUID.nameUUIDFromBytes((ownerUserId + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .set(albums.ownerUserId, ownerUserId)
                .set(albums.name, name)
                .set(albums.kind, AlbumKind.USER)
                .set(albums.createdAt, NOW)
                .set(albums.updatedAt, NOW)
                .execute();
    }

    private void tag(UUID ownerUserId, String name) {
        QTags tags = QTags.tags;
        queryFactory.insert(tags)
                .set(tags.id, UUID.nameUUIDFromBytes((ownerUserId + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .set(tags.ownerUserId, ownerUserId)
                .set(tags.name, name)
                .set(tags.normalizedName, name.toLowerCase(java.util.Locale.ROOT))
                .set(tags.createdBy, ownerUserId)
                .set(tags.createdAt, NOW)
                .set(tags.updatedAt, NOW)
                .execute();
    }
}
