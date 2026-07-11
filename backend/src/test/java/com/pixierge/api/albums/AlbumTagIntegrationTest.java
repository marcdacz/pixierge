package com.pixierge.api.albums;

import com.pixierge.api.db.QAlbumItems;
import com.pixierge.api.db.QAlbums;
import com.pixierge.api.db.QAssetFiles;
import com.pixierge.api.db.QAssetTags;
import com.pixierge.api.db.QAssets;
import com.pixierge.api.db.QLibraries;
import com.pixierge.api.db.QLibraryRoots;
import com.pixierge.api.db.QPasswordCredentials;
import com.pixierge.api.db.QSessions;
import com.pixierge.api.db.QTags;
import com.pixierge.api.db.QUserRoles;
import com.pixierge.api.db.QUsers;
import com.querydsl.sql.SQLQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AlbumTagIntegrationTest {
    private static final String PASSWORD = "correct horse battery staple";
    private static final String CSRF_HEADER = "X-Pixierge-Csrf";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private SQLQueryFactory queryFactory;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void clearData() {
        transactionTemplate.executeWithoutResult(status -> {
            queryFactory.delete(QAssetFiles.assetFiles).execute();
            queryFactory.delete(QAlbumItems.albumItems).execute();
            queryFactory.delete(QAssetTags.assetTags).execute();
            queryFactory.delete(QAlbums.albums).execute();
            queryFactory.delete(QTags.tags).execute();
            queryFactory.delete(QAssets.assets).execute();
            queryFactory.delete(QLibraryRoots.libraryRoots).execute();
            queryFactory.delete(QLibraries.libraries).execute();
            queryFactory.delete(QSessions.sessions).execute();
            queryFactory.delete(QUserRoles.userRoles).execute();
            queryFactory.delete(QPasswordCredentials.passwordCredentials).execute();
            queryFactory.delete(QUsers.users).execute();
        });
    }

    @Test
    void albumsAreUniquePerOwnerAndKeepSourceLibraryAssignments() {
        ResponseEntity<Map> admin = createAdmin();
        Fixture fixture = fixture();
        String cookie = cookie(admin);
        String csrf = csrf(admin);
        ResponseEntity<Map> created = exchange("/api/albums", HttpMethod.POST, request(cookie, csrf,
                Map.of("name", "Summer")), Map.class);
        String albumId = created.getBody().get("id").toString();
        ResponseEntity<Map> duplicate = exchange("/api/albums", HttpMethod.POST, request(cookie, csrf,
                Map.of("name", "summer")), Map.class);
        ResponseEntity<Void> firstAdd = exchange("/api/album-items", HttpMethod.POST, request(cookie, csrf, Map.of(
                "albumIds", List.of(albumId),
                "items", List.of(Map.of("assetId", fixture.firstAsset(), "sourceLibraryId", fixture.firstLibrary())))),
                Void.class);
        ResponseEntity<Void> mixedAdd = exchange("/api/album-items", HttpMethod.POST, request(cookie, csrf, Map.of(
                "albumIds", List.of(albumId),
                "items", List.of(
                        Map.of("assetId", fixture.firstAsset(), "sourceLibraryId", fixture.firstLibrary()),
                        Map.of("assetId", fixture.secondAsset(), "sourceLibraryId", fixture.secondLibrary())))), Void.class);
        ResponseEntity<Map> assets = exchange("/api/albums/" + albumId + "/assets?pageSize=2", HttpMethod.GET,
                cookieRequest(cookie), Map.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(firstAdd.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(mixedAdd.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(assets.getBody()).containsEntry("totalCount", 2).containsEntry("pageSize", 2);
        List<Map<String, Object>> sections = (List<Map<String, Object>>) assets.getBody().get("sections");
        List<Map<String, Object>> firstSectionAssets = (List<Map<String, Object>>) sections.get(0).get("assets");
        assertThat(firstSectionAssets.get(0)).containsEntry("libraryId", fixture.firstLibrary().toString());
        List<UUID> sourceLibraryIds = transactionTemplate.execute(status -> queryFactory
                .select(QAlbumItems.albumItems.sourceLibraryId)
                .from(QAlbumItems.albumItems)
                .where(QAlbumItems.albumItems.albumId.eq(UUID.fromString(albumId)))
                .fetch());
        assertThat(sourceLibraryIds)
                .containsExactlyInAnyOrder(fixture.firstLibrary(), fixture.secondLibrary());
    }

    @Test
    void tagsFilterAssetsAndAssetDetailIncludesTags() {
        ResponseEntity<Map> admin = createAdmin();
        Fixture fixture = fixture();
        String cookie = cookie(admin);
        String csrf = csrf(admin);
        ResponseEntity<Map> tag = exchange("/api/tags", HttpMethod.POST, request(cookie, csrf, Map.of("name", "Family")), Map.class);
        String tagId = tag.getBody().get("id").toString();
        ResponseEntity<Void> assigned = exchange("/api/asset-tags", HttpMethod.POST, request(cookie, csrf, Map.of(
                "tagIds", List.of(tagId),
                "items", List.of(Map.of("assetId", fixture.firstAsset(), "sourceLibraryId", fixture.firstLibrary())))), Void.class);
        ResponseEntity<Void> duplicateAssigned = exchange("/api/asset-tags", HttpMethod.POST, request(cookie, csrf, Map.of(
                "tagIds", List.of(tagId),
                "items", List.of(Map.of("assetId", fixture.firstAsset(), "sourceLibraryId", fixture.firstLibrary())))), Void.class);
        ResponseEntity<Map> browse = exchange("/api/assets?tag=" + tagId, HttpMethod.GET, cookieRequest(cookie), Map.class);
        ResponseEntity<Map> detail = exchange("/api/assets/" + fixture.firstAsset(), HttpMethod.GET, cookieRequest(cookie), Map.class);
        ResponseEntity<Void> deleted = exchange("/api/tags/" + tagId, HttpMethod.DELETE, request(cookie, csrf, null), Void.class);

        assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(duplicateAssigned.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(browse.getBody()).containsEntry("totalCount", 1);
        assertThat(detail.getBody().get("tags")).asList().hasSize(1);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        Long remainingTags = transactionTemplate.execute(status -> queryFactory
                .select(QAssetTags.assetTags.tagId.count())
                .from(QAssetTags.assetTags)
                .fetchOne());
        assertThat(remainingTags).isZero();
    }

    @Test
    void favouritesAreHiddenFromAlbumListAndSupportAddRemoveBrowse() {
        ResponseEntity<Map> admin = createAdmin();
        Fixture fixture = fixture();
        String cookie = cookie(admin);
        String csrf = csrf(admin);

        ResponseEntity<Map> first = exchange("/api/favourites", HttpMethod.GET, cookieRequest(cookie), Map.class);
        ResponseEntity<Map> second = exchange("/api/favourites", HttpMethod.GET, cookieRequest(cookie), Map.class);
        String favouritesId = first.getBody().get("id").toString();

        ResponseEntity<Map> userAlbum = exchange("/api/albums", HttpMethod.POST, request(cookie, csrf,
                Map.of("name", "Summer")), Map.class);
        ResponseEntity<Map> reservedName = exchange("/api/albums", HttpMethod.POST, request(cookie, csrf,
                Map.of("name", "Favourites")), Map.class);
        ResponseEntity<Map[]> albums = exchange("/api/albums", HttpMethod.GET, cookieRequest(cookie), Map[].class);

        ResponseEntity<Void> added = exchange("/api/album-items", HttpMethod.POST, request(cookie, csrf, Map.of(
                "albumIds", List.of(favouritesId),
                "items", List.of(Map.of("assetId", fixture.firstAsset(), "sourceLibraryId", fixture.firstLibrary())))),
                Void.class);
        ResponseEntity<Map> assets = exchange("/api/favourites/assets?pageSize=10", HttpMethod.GET,
                cookieRequest(cookie), Map.class);
        ResponseEntity<Void> removed = exchange("/api/albums/" + favouritesId + "/items", HttpMethod.DELETE,
                request(cookie, csrf, Map.of("assetIds", List.of(fixture.firstAsset()))), Void.class);
        ResponseEntity<Map> emptyAssets = exchange("/api/favourites/assets?pageSize=10", HttpMethod.GET,
                cookieRequest(cookie), Map.class);
        ResponseEntity<Map> rename = exchange("/api/albums/" + favouritesId, HttpMethod.PATCH,
                request(cookie, csrf, Map.of("name", "Stars")), Map.class);
        ResponseEntity<Void> delete = exchange("/api/albums/" + favouritesId, HttpMethod.DELETE,
                request(cookie, csrf, null), Void.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).containsEntry("kind", "favourites").containsEntry("name", "Favourites");
        assertThat(second.getBody()).containsEntry("id", favouritesId);
        assertThat(userAlbum.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reservedName.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(albums.getBody()).hasSize(1);
        assertThat(albums.getBody()[0]).containsEntry("name", "Summer").containsEntry("kind", "user");
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(assets.getBody()).containsEntry("totalCount", 1);
        List<Map<String, Object>> sections = (List<Map<String, Object>>) assets.getBody().get("sections");
        List<Map<String, Object>> sectionAssets = (List<Map<String, Object>>) sections.get(0).get("assets");
        assertThat(sectionAssets.get(0)).containsEntry("favourited", true);
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(emptyAssets.getBody()).containsEntry("totalCount", 0);
        assertThat(rename.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private Fixture fixture() {
        return transactionTemplate.execute(status -> {
            UUID userId = queryFactory.select(QUsers.users.id).from(QUsers.users).fetchFirst();
            OffsetDateTime now = OffsetDateTime.now();
            UUID firstLibrary = library(userId, "One", now);
            UUID secondLibrary = library(userId, "Two", now);
            UUID firstAsset = asset(firstLibrary, "one.jpg", now);
            UUID secondAsset = asset(secondLibrary, "two.jpg", now);
            assetOccurrence(firstAsset, secondLibrary, "copy.jpg", "/aaa/copy.jpg", now);
            return new Fixture(firstLibrary, secondLibrary, firstAsset, secondAsset);
        });
    }

    private UUID library(UUID userId, String name, OffsetDateTime now) {
        UUID id = UUID.randomUUID();
        UUID root = UUID.randomUUID();
        queryFactory.insert(QLibraries.libraries).set(QLibraries.libraries.id, id).set(QLibraries.libraries.name, name)
                .set(QLibraries.libraries.createdBy, userId).set(QLibraries.libraries.status, "active")
                .set(QLibraries.libraries.createdAt, now).set(QLibraries.libraries.updatedAt, now).execute();
        queryFactory.insert(QLibraryRoots.libraryRoots).set(QLibraryRoots.libraryRoots.id, root)
                .set(QLibraryRoots.libraryRoots.libraryId, id).set(QLibraryRoots.libraryRoots.path, "/tmp/" + name)
                .set(QLibraryRoots.libraryRoots.normalizedPath, "/tmp/" + name)
                .set(QLibraryRoots.libraryRoots.createdAt, now).set(QLibraryRoots.libraryRoots.updatedAt, now).execute();
        return id;
    }

    private UUID asset(UUID libraryId, String fileName, OffsetDateTime now) {
        UUID asset = UUID.randomUUID();
        UUID root = queryFactory.select(QLibraryRoots.libraryRoots.id).from(QLibraryRoots.libraryRoots)
                .where(QLibraryRoots.libraryRoots.libraryId.eq(libraryId)).fetchOne();
        String hash = UUID.randomUUID().toString();
        queryFactory.insert(QAssets.assets).set(QAssets.assets.id, asset).set(QAssets.assets.contentHash, hash)
                .set(QAssets.assets.mediaType, "image/jpeg").set(QAssets.assets.availableFileCount, 1)
                .set(QAssets.assets.firstObservedAt, now).set(QAssets.assets.lastObservedAt, now).execute();
        queryFactory.insert(QAssetFiles.assetFiles).set(QAssetFiles.assetFiles.id, UUID.randomUUID())
                .set(QAssetFiles.assetFiles.assetId, asset).set(QAssetFiles.assetFiles.libraryId, libraryId)
                .set(QAssetFiles.assetFiles.rootId, root).set(QAssetFiles.assetFiles.path, "/tmp/" + fileName)
                .set(QAssetFiles.assetFiles.normalizedPath, "/tmp/" + fileName).set(QAssetFiles.assetFiles.fileName, fileName)
                .set(QAssetFiles.assetFiles.sizeBytes, 1L).set(QAssetFiles.assetFiles.modifiedAt, now)
                .set(QAssetFiles.assetFiles.contentHash, hash).set(QAssetFiles.assetFiles.status, "active")
                .set(QAssetFiles.assetFiles.firstObservedAt, now).set(QAssetFiles.assetFiles.lastObservedAt, now).execute();
        return asset;
    }

    private void assetOccurrence(UUID asset, UUID libraryId, String fileName, String path, OffsetDateTime now) {
        UUID root = queryFactory.select(QLibraryRoots.libraryRoots.id).from(QLibraryRoots.libraryRoots)
                .where(QLibraryRoots.libraryRoots.libraryId.eq(libraryId)).fetchOne();
        String hash = queryFactory.select(QAssets.assets.contentHash).from(QAssets.assets)
                .where(QAssets.assets.id.eq(asset)).fetchOne();
        queryFactory.insert(QAssetFiles.assetFiles).set(QAssetFiles.assetFiles.id, UUID.randomUUID())
                .set(QAssetFiles.assetFiles.assetId, asset).set(QAssetFiles.assetFiles.libraryId, libraryId)
                .set(QAssetFiles.assetFiles.rootId, root).set(QAssetFiles.assetFiles.path, path)
                .set(QAssetFiles.assetFiles.normalizedPath, path).set(QAssetFiles.assetFiles.fileName, fileName)
                .set(QAssetFiles.assetFiles.sizeBytes, 1L).set(QAssetFiles.assetFiles.modifiedAt, now)
                .set(QAssetFiles.assetFiles.contentHash, hash).set(QAssetFiles.assetFiles.status, "active")
                .set(QAssetFiles.assetFiles.firstObservedAt, now).set(QAssetFiles.assetFiles.lastObservedAt, now).execute();
    }

    private ResponseEntity<Map> createAdmin() {
        return restTemplate.postForEntity("/api/setup/admin", Map.of("username", "admin", "password", PASSWORD), Map.class);
    }

    private <T> ResponseEntity<T> exchange(String path, HttpMethod method, HttpEntity<?> request, Class<T> type) {
        return restTemplate.exchange(path, method, request, type);
    }

    private HttpEntity<Object> request(String cookie, String csrf, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        headers.add(CSRF_HEADER, csrf);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Void> cookieRequest(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return new HttpEntity<>(headers);
    }

    private String cookie(ResponseEntity<?> response) {
        return response.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
    }

    private String csrf(ResponseEntity<Map> response) {
        return response.getBody().get("csrfToken").toString();
    }

    private record Fixture(UUID firstLibrary, UUID secondLibrary, UUID firstAsset, UUID secondAsset) {
    }
}
