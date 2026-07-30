package com.pixierge.api.identity;

import com.pixierge.api.db.QAlbumItems;
import com.pixierge.api.db.QAlbums;
import com.pixierge.api.db.QAssetTags;
import com.pixierge.api.db.QAssets;
import com.pixierge.api.db.QLibraries;
import com.pixierge.api.db.QLibraryMembers;
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
class IdentityIntegrationTest {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "correct horse battery staple";

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
    void clearUsers() {
        transactionTemplate.executeWithoutResult(status -> {
            queryFactory.delete(QAlbumItems.albumItems).execute();
            queryFactory.delete(QAssetTags.assetTags).execute();
            queryFactory.delete(QAlbums.albums).execute();
            queryFactory.delete(QTags.tags).execute();
            queryFactory.delete(QAssets.assets).execute();
            queryFactory.delete(QLibraryMembers.libraryMembers).execute();
            queryFactory.delete(QLibraries.libraries).execute();
            queryFactory.delete(QSessions.sessions).execute();
            queryFactory.delete(QUserRoles.userRoles).execute();
            queryFactory.delete(QPasswordCredentials.passwordCredentials).execute();
            queryFactory.delete(QUsers.users).execute();
        });
    }

    @Test
    void setupCreatesFirstAdminAndRejectsDuplicateSetup() {
        ResponseEntity<Map> statusBefore = restTemplate.getForEntity("/api/setup/status", Map.class);

        ResponseEntity<Map> setup = createFirstAdmin();
        ResponseEntity<Map> statusAfter = restTemplate.getForEntity("/api/setup/status", Map.class);
        ResponseEntity<Map> duplicateSetup = restTemplate.postForEntity("/api/setup/admin", adminSetupBody(), Map.class);

        assertThat(statusBefore.getBody()).containsEntry("required", true);
        assertThat(setup.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(setup.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains(IdentityConstants.SESSION_COOKIE_NAME);
        assertThat(userBody(setup)).containsEntry("username", ADMIN_USERNAME);
        assertThat(statusAfter.getBody()).containsEntry("required", false);
        assertThat(duplicateSetup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void loginSessionAndProtectedAdminEndpointWork() {
        createFirstAdmin();

        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/api/auth/login",
                Map.of("username", ADMIN_USERNAME, "password", ADMIN_PASSWORD),
                Map.class
        );

        String cookie = cookiePair(login);
        String csrfToken = csrfToken(login);
        ResponseEntity<List> users = restTemplate.exchange(
                "/api/admin/users",
                HttpMethod.GET,
                withCookie(cookie),
                List.class
        );
        ResponseEntity<List> roles = restTemplate.exchange(
                "/api/admin/roles",
                HttpMethod.GET,
                withCookie(cookie),
                List.class
        );
        ResponseEntity<Map> session = restTemplate.exchange(
                "/api/auth/session",
                HttpMethod.GET,
                withCookie(cookie),
                Map.class
        );
        ResponseEntity<Void> logout = restTemplate.exchange(
                "/api/auth/logout",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken),
                Void.class
        );
        ResponseEntity<Map> revokedSession = restTemplate.exchange(
                "/api/auth/session",
                HttpMethod.GET,
                withCookie(cookie),
                Map.class
        );

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(users.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(users.getBody()).hasSize(1);
        assertThat(roles.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(roles.getBody()).extracting(role -> ((Map<?, ?>) role).get("key"))
                .contains("ADMIN", "USER");
        assertThat(session.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userBody(session)).containsEntry("username", ADMIN_USERNAME);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(revokedSession.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedAdminEndpointRequiresSessionAndMutationsRequireCsrf() {
        ResponseEntity<Map> noSession = restTemplate.getForEntity("/api/admin/users", Map.class);
        ResponseEntity<Map> setup = createFirstAdmin();

        String cookie = cookiePair(setup);
        ResponseEntity<Map> missingCsrf = restTemplate.exchange(
                "/api/auth/logout",
                HttpMethod.POST,
                withCookie(cookie),
                Map.class
        );

        assertThat(noSession.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(missingCsrf.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanCreateResetDeactivateAndReactivateAStandardUser() {
        ResponseEntity<Map> admin = createFirstAdmin();
        String cookie = cookiePair(admin);
        String csrfToken = csrfToken(admin);

        ResponseEntity<Map> created = restTemplate.exchange(
                "/api/admin/users",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("username", "sam", "password", "a secure password")),
                Map.class
        );
        String userId = (String) created.getBody().get("id");

        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/api/auth/login", Map.of("username", "sam", "password", "a secure password"), Map.class);
        ResponseEntity<Void> reset = restTemplate.exchange(
                "/api/admin/users/" + userId + "/reset-password", HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("password", "a different secure password")), Void.class);
        ResponseEntity<Map> revokedSession = restTemplate.exchange("/api/auth/session", HttpMethod.GET, withCookie(cookiePair(login)), Map.class);
        ResponseEntity<Map> deactivated = restTemplate.exchange(
                "/api/admin/users/" + userId, HttpMethod.PATCH,
                withCookieAndCsrf(cookie, csrfToken, Map.of("active", false)), Map.class);
        ResponseEntity<Map> blockedLogin = restTemplate.postForEntity(
                "/api/auth/login", Map.of("username", "sam", "password", "a different secure password"), Map.class);
        ResponseEntity<Map> reactivated = restTemplate.exchange(
                "/api/admin/users/" + userId, HttpMethod.PATCH,
                withCookieAndCsrf(cookie, csrfToken, Map.of("active", true)), Map.class);
        ResponseEntity<Map> newLogin = restTemplate.postForEntity(
                "/api/auth/login", Map.of("username", "sam", "password", "a different secure password"), Map.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(created.getBody()).containsEntry("username", "sam").containsEntry("status", "active");
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(revokedSession.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(deactivated.getBody()).containsEntry("status", "disabled");
        assertThat(blockedLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(reactivated.getBody()).containsEntry("status", "active");
        assertThat(newLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminUserCreationRejectsDuplicatesAndNonAdminCallers() {
        ResponseEntity<Map> admin = createFirstAdmin();
        String adminCookie = cookiePair(admin);
        String adminCsrf = csrfToken(admin);
        ResponseEntity<Map> created = createStandardUser(adminCookie, adminCsrf, "sam", "a secure password");
        ResponseEntity<Map> duplicate = restTemplate.exchange(
                "/api/admin/users",
                HttpMethod.POST,
                withCookieAndCsrf(adminCookie, adminCsrf, Map.of("username", "SAM", "password", "a secure password")),
                Map.class
        );
        ResponseEntity<Map> standardLogin = restTemplate.postForEntity(
                "/api/auth/login", Map.of("username", "sam", "password", "a secure password"), Map.class);
        ResponseEntity<Map> nonAdminCreate = restTemplate.exchange(
                "/api/admin/users",
                HttpMethod.POST,
                withCookieAndCsrf(cookiePair(standardLogin), csrfToken(standardLogin),
                        Map.of("username", "lee", "password", "another secure password")),
                Map.class
        );

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(created.getBody()).containsEntry("username", "sam");
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(nonAdminCreate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deleteTransfersOwnershipAndRevokesSessions() {
        ResponseEntity<Map> admin = createFirstAdmin();
        String adminCookie = cookiePair(admin);
        String adminCsrf = csrfToken(admin);
        UUID departingUserId = UUID.fromString((String) createStandardUser(adminCookie, adminCsrf, "sam", "a secure password").getBody().get("id"));
        UUID replacementUserId = UUID.fromString((String) createStandardUser(adminCookie, adminCsrf, "lee", "another secure password").getBody().get("id"));
        ResponseEntity<Map> departingLogin = restTemplate.postForEntity(
                "/api/auth/login", Map.of("username", "sam", "password", "a secure password"), Map.class);
        String departingCookie = cookiePair(departingLogin);
        UUID libraryId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        seedOwnedCatalogRows(departingUserId, replacementUserId, libraryId, albumId, tagId, assetId);

        ResponseEntity<Void> deleted = restTemplate.exchange(
                "/api/admin/users/" + departingUserId,
                HttpMethod.DELETE,
                withCookieAndCsrf(adminCookie, adminCsrf, Map.of("replacementUserId", replacementUserId)),
                Void.class
        );
        ResponseEntity<Map> revokedSession = restTemplate.exchange(
                "/api/auth/session", HttpMethod.GET, withCookie(departingCookie), Map.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(revokedSession.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        transactionTemplate.executeWithoutResult(status -> {
            assertThat(queryFactory.select(QUsers.users.id).from(QUsers.users).where(QUsers.users.id.eq(departingUserId)).fetchOne())
                    .isNull();
            assertThat(queryFactory.select(QLibraryMembers.libraryMembers.memberRole).from(QLibraryMembers.libraryMembers)
                    .where(QLibraryMembers.libraryMembers.libraryId.eq(libraryId)
                            .and(QLibraryMembers.libraryMembers.userId.eq(replacementUserId))).fetchOne())
                    .isEqualTo("owner");
            assertThat(queryFactory.select(QAlbums.albums.ownerUserId).from(QAlbums.albums)
                    .where(QAlbums.albums.id.eq(albumId)).fetchOne()).isEqualTo(replacementUserId);
            assertThat(queryFactory.select(QAlbumItems.albumItems.addedBy).from(QAlbumItems.albumItems)
                    .where(QAlbumItems.albumItems.albumId.eq(albumId)).fetchOne()).isEqualTo(replacementUserId);
            assertThat(queryFactory.select(QTags.tags.ownerUserId).from(QTags.tags)
                    .where(QTags.tags.id.eq(tagId)).fetchOne()).isEqualTo(replacementUserId);
            assertThat(queryFactory.select(QAssetTags.assetTags.addedBy).from(QAssetTags.assetTags)
                    .where(QAssetTags.assetTags.tagId.eq(tagId)).fetchOne()).isEqualTo(replacementUserId);
        });
    }

    @Test
    void deleteRejectsSelfDeletionSelfReplacementAndInactiveReplacement() {
        ResponseEntity<Map> admin = createFirstAdmin();
        String adminCookie = cookiePair(admin);
        String adminCsrf = csrfToken(admin);
        UUID adminUserId = UUID.fromString((String) userBody(admin).get("id"));
        UUID departingUserId = UUID.fromString((String) createStandardUser(adminCookie, adminCsrf, "sam", "a secure password").getBody().get("id"));
        UUID replacementUserId = UUID.fromString((String) createStandardUser(adminCookie, adminCsrf, "lee", "another secure password").getBody().get("id"));

        ResponseEntity<Map> selfDelete = restTemplate.exchange(
                "/api/admin/users/" + adminUserId,
                HttpMethod.DELETE,
                withCookieAndCsrf(adminCookie, adminCsrf, Map.of("replacementUserId", replacementUserId)),
                Map.class
        );
        ResponseEntity<Map> selfReplacement = restTemplate.exchange(
                "/api/admin/users/" + departingUserId,
                HttpMethod.DELETE,
                withCookieAndCsrf(adminCookie, adminCsrf, Map.of("replacementUserId", departingUserId)),
                Map.class
        );
        restTemplate.exchange(
                "/api/admin/users/" + replacementUserId, HttpMethod.PATCH,
                withCookieAndCsrf(adminCookie, adminCsrf, Map.of("active", false)), Map.class);
        ResponseEntity<Map> inactiveReplacement = restTemplate.exchange(
                "/api/admin/users/" + departingUserId,
                HttpMethod.DELETE,
                withCookieAndCsrf(adminCookie, adminCsrf, Map.of("replacementUserId", replacementUserId)),
                Map.class
        );

        assertThat(selfDelete.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(selfReplacement.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(inactiveReplacement.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<Map> createFirstAdmin() {
        return restTemplate.postForEntity("/api/setup/admin", adminSetupBody(), Map.class);
    }

    private ResponseEntity<Map> createStandardUser(String cookie, String csrfToken, String username, String password) {
        return restTemplate.exchange(
                "/api/admin/users",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("username", username, "password", password)),
                Map.class
        );
    }

    private void seedOwnedCatalogRows(UUID departingUserId, UUID replacementUserId, UUID libraryId, UUID albumId, UUID tagId, UUID assetId) {
        transactionTemplate.executeWithoutResult(status -> {
            OffsetDateTime now = OffsetDateTime.now();
            queryFactory.insert(QLibraries.libraries)
                    .set(QLibraries.libraries.id, libraryId)
                    .set(QLibraries.libraries.name, "Departing Library")
                    .set(QLibraries.libraries.createdBy, departingUserId)
                    .set(QLibraries.libraries.createdAt, now)
                    .set(QLibraries.libraries.updatedAt, now)
                    .execute();
            queryFactory.insert(QLibraryMembers.libraryMembers)
                    .set(QLibraryMembers.libraryMembers.libraryId, libraryId)
                    .set(QLibraryMembers.libraryMembers.userId, departingUserId)
                    .set(QLibraryMembers.libraryMembers.memberRole, "owner")
                    .set(QLibraryMembers.libraryMembers.createdAt, now)
                    .execute();
            queryFactory.insert(QLibraryMembers.libraryMembers)
                    .set(QLibraryMembers.libraryMembers.libraryId, libraryId)
                    .set(QLibraryMembers.libraryMembers.userId, replacementUserId)
                    .set(QLibraryMembers.libraryMembers.memberRole, "member")
                    .set(QLibraryMembers.libraryMembers.createdAt, now)
                    .execute();
            queryFactory.insert(QAssets.assets)
                    .set(QAssets.assets.id, assetId)
                    .set(QAssets.assets.contentHash, "delete-transfer-content-hash")
                    .set(QAssets.assets.mediaType, "image/jpeg")
                    .set(QAssets.assets.availableFileCount, 1)
                    .set(QAssets.assets.firstObservedAt, now)
                    .set(QAssets.assets.lastObservedAt, now)
                    .execute();
            queryFactory.insert(QAlbums.albums)
                    .set(QAlbums.albums.id, albumId)
                    .set(QAlbums.albums.ownerUserId, departingUserId)
                    .set(QAlbums.albums.name, "Departing Album")
                    .set(QAlbums.albums.createdAt, now)
                    .set(QAlbums.albums.updatedAt, now)
                    .execute();
            queryFactory.insert(QAlbumItems.albumItems)
                    .set(QAlbumItems.albumItems.albumId, albumId)
                    .set(QAlbumItems.albumItems.assetId, assetId)
                    .set(QAlbumItems.albumItems.sourceLibraryId, libraryId)
                    .set(QAlbumItems.albumItems.position, 0)
                    .set(QAlbumItems.albumItems.addedBy, departingUserId)
                    .set(QAlbumItems.albumItems.createdAt, now)
                    .set(QAlbumItems.albumItems.updatedAt, now)
                    .execute();
            queryFactory.insert(QTags.tags)
                    .set(QTags.tags.id, tagId)
                    .set(QTags.tags.ownerUserId, departingUserId)
                    .set(QTags.tags.name, "Departing Tag")
                    .set(QTags.tags.normalizedName, "departing tag")
                    .set(QTags.tags.createdBy, departingUserId)
                    .set(QTags.tags.createdAt, now)
                    .set(QTags.tags.updatedAt, now)
                    .execute();
            queryFactory.insert(QAssetTags.assetTags)
                    .set(QAssetTags.assetTags.tagId, tagId)
                    .set(QAssetTags.assetTags.assetId, assetId)
                    .set(QAssetTags.assetTags.sourceLibraryId, libraryId)
                    .set(QAssetTags.assetTags.addedBy, departingUserId)
                    .set(QAssetTags.assetTags.createdAt, now)
                    .execute();
        });
    }

    private Map<String, String> adminSetupBody() {
        return Map.of(
                "username", ADMIN_USERNAME,
                "password", ADMIN_PASSWORD
        );
    }

    private HttpEntity<Void> withCookie(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Void> withCookieAndCsrf(String cookie, String csrfToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        headers.add(IdentityConstants.CSRF_HEADER, csrfToken);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Map<String, ?>> withCookieAndCsrf(String cookie, String csrfToken, Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        headers.add(IdentityConstants.CSRF_HEADER, csrfToken);
        return new HttpEntity<>(body, headers);
    }

    private String cookiePair(ResponseEntity<?> response) {
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotBlank();
        return setCookie.split(";", 2)[0];
    }

    private String csrfToken(ResponseEntity<Map> response) {
        Object token = response.getBody().get("csrfToken");
        assertThat(token).isInstanceOf(String.class);
        return (String) token;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> userBody(ResponseEntity<Map> response) {
        Object user = response.getBody().get("user");
        assertThat(user).isInstanceOf(Map.class);
        return (Map<String, Object>) user;
    }
}
