package com.pixierge.api.libraries;

import com.pixierge.api.db.QAssetFiles;
import com.pixierge.api.db.QAlbumItems;
import com.pixierge.api.db.QAlbums;
import com.pixierge.api.db.QAssetTags;
import com.pixierge.api.db.QAssets;
import com.pixierge.api.db.QFileObservations;
import com.pixierge.api.db.QGlobalExclusionPatterns;
import com.pixierge.api.db.QLibraries;
import com.pixierge.api.db.QLibraryExclusionPatterns;
import com.pixierge.api.db.QLibraryMembers;
import com.pixierge.api.db.QLibraryRoots;
import com.pixierge.api.db.QPasswordCredentials;
import com.pixierge.api.db.QScanErrors;
import com.pixierge.api.db.QScanRuns;
import com.pixierge.api.db.QSessions;
import com.pixierge.api.db.QThumbnails;
import com.pixierge.api.db.QTags;
import com.pixierge.api.db.QUserRoles;
import com.pixierge.api.db.QUsers;
import com.pixierge.api.identity.UserRepository;
import com.querydsl.sql.SQLQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LibraryIntegrationTest {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "correct horse battery staple";
    private static final String CSRF_HEADER = "X-Pixierge-Csrf";
    private static final String USER_USERNAME = "viewer";
    private static final String USER_PASSWORD = "correct horse battery staple";
    private static final String USER_ROLE = "USER";
    private static final List<String> SEEDED_GLOBAL_EXCLUSIONS = List.of(
            "**/@eaDir/**",
            "**/#recycle/**",
            "**/#snapshot/**",
            "**/.stversions/**",
            "**/.stfolder/**",
            "**/._*"
    );
    private static final Path THUMBNAIL_STORAGE_ROOT = createThumbnailStorageRoot();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void thumbnailStorageProperties(DynamicPropertyRegistry registry) {
        registry.add("pixierge.storage.root", THUMBNAIL_STORAGE_ROOT::toString);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SQLQueryFactory queryFactory;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void clearData() throws IOException {
        clearThumbnailStorage();
        transactionTemplate.executeWithoutResult(status -> {
            queryFactory.delete(QFileObservations.fileObservations).execute();
            queryFactory.delete(QScanErrors.scanErrors).execute();
            queryFactory.delete(QThumbnails.thumbnails).execute();
            queryFactory.delete(QAssetFiles.assetFiles).execute();
            queryFactory.delete(QAlbumItems.albumItems).execute();
            queryFactory.delete(QAssetTags.assetTags).execute();
            queryFactory.delete(QAlbums.albums).execute();
            queryFactory.delete(QTags.tags).execute();
            queryFactory.delete(QScanRuns.scanRuns).execute();
            queryFactory.delete(QAssets.assets).execute();
            queryFactory.delete(QLibraryExclusionPatterns.libraryExclusionPatterns).execute();
            queryFactory.delete(QGlobalExclusionPatterns.globalExclusionPatterns)
                    .where(QGlobalExclusionPatterns.globalExclusionPatterns.pattern.notIn(SEEDED_GLOBAL_EXCLUSIONS))
                    .execute();
            queryFactory.delete(QLibraryRoots.libraryRoots).execute();
            queryFactory.delete(QLibraryMembers.libraryMembers).execute();
            queryFactory.delete(QLibraries.libraries).execute();
            queryFactory.delete(QSessions.sessions).execute();
            queryFactory.delete(QUserRoles.userRoles).execute();
            queryFactory.delete(QPasswordCredentials.passwordCredentials).execute();
            queryFactory.delete(QUsers.users).execute();
        });
    }

    @Test
    void adminCreatesLibraryAndManagesSourceHealth() throws Exception {
        ResponseEntity<Map> admin = createFirstAdmin();
        String cookie = cookiePair(admin);
        String csrfToken = csrfToken(admin);
        Path familySource = Files.createDirectory(tempDir.resolve("family"));
        Path regularFile = Files.createFile(tempDir.resolve("not-a-directory.txt"));

        ResponseEntity<Map> createdLibrary = restTemplate.exchange(
                "/api/libraries",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("name", "Family Photos")),
                Map.class
        );
        String libraryId = createdLibrary.getBody().get("id").toString();
        ResponseEntity<Map> renamedLibrary = restTemplate.exchange(
                "/api/libraries/" + libraryId,
                HttpMethod.PATCH,
                withCookieAndCsrf(cookie, csrfToken, Map.of("name", "Family Archive")),
                Map.class
        );

        ResponseEntity<Map> addedSource = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/roots",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("path", familySource.toString())),
                Map.class
        );
        String rootId = sourceRows(addedSource).get(0).get("id").toString();
        ResponseEntity<Map> duplicateSource = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/roots",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("path", familySource.toString())),
                Map.class
        );
        ResponseEntity<Map> missingSource = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/roots",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("path", tempDir.resolve("missing").toString())),
                Map.class
        );
        ResponseEntity<Map> fileSource = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/roots",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("path", regularFile.toString())),
                Map.class
        );
        ResponseEntity<Map> archivedLibrary = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/archive",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, null),
                Map.class
        );
        ResponseEntity<Map> archivedScan = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/scans",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, null),
                Map.class
        );
        ResponseEntity<Map> restoredLibrary = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/restore",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, null),
                Map.class
        );
        ResponseEntity<Map> addedExclusion = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/exclusion-patterns",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("pattern", "**/.pixierge-cache/**")),
                Map.class
        );
        String exclusionId = exclusionRows(addedExclusion).stream()
                .filter(row -> row.get("pattern").equals("**/.pixierge-cache/**"))
                .findFirst()
                .orElseThrow()
                .get("id")
                .toString();
        ResponseEntity<Map> duplicateExclusion = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/exclusion-patterns",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("pattern", "**/.pixierge-cache/**")),
                Map.class
        );
        ResponseEntity<Void> deletedExclusion = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/exclusion-patterns/" + exclusionId,
                HttpMethod.DELETE,
                withCookieAndCsrf(cookie, csrfToken, null),
                Void.class
        );
        Files.delete(familySource);
        ResponseEntity<List> staleList = restTemplate.exchange(
                "/api/libraries",
                HttpMethod.GET,
                withCookie(cookie),
                List.class
        );

        ResponseEntity<Void> deleted = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/roots/" + rootId,
                HttpMethod.DELETE,
                withCookieAndCsrf(cookie, csrfToken, null),
                Void.class
        );
        ResponseEntity<List> afterDelete = restTemplate.exchange(
                "/api/libraries",
                HttpMethod.GET,
                withCookie(cookie),
                List.class
        );

        assertThat(createdLibrary.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createdLibrary.getBody()).containsEntry("name", "Family Photos");
        assertThat(createdLibrary.getBody()).containsEntry("status", "active");
        assertThat(renamedLibrary.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(renamedLibrary.getBody()).containsEntry("name", "Family Archive");
        assertThat(addedSource.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(addedSource.getBody()).containsEntry("sourceCount", 1);
        assertThat(duplicateSource.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(missingSource.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fileSource.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(archivedLibrary.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(archivedLibrary.getBody()).containsEntry("status", "archived");
        assertThat(archivedScan.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(restoredLibrary.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restoredLibrary.getBody()).containsEntry("status", "active");
        assertThat(addedExclusion.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(duplicateExclusion.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(deletedExclusion.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(staleList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstLibrary(staleList)).containsEntry("unavailableSourceCount", 1);
        assertThat(firstSource(staleList)).containsEntry("available", false);
        assertThat(firstSource(staleList)).containsEntry("unavailableReason", "missing");
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(firstLibrary(afterDelete)).containsEntry("sourceCount", 0);
    }

    @Test
    void adminScansSourcesAndReconcilesAssetIdentity() throws Exception {
        ResponseEntity<Map> admin = createFirstAdmin();
        String cookie = cookiePair(admin);
        String csrfToken = csrfToken(admin);
        Path source = Files.createDirectory(tempDir.resolve("family"));
        Path beach = source.resolve("beach.jpg");
        Path birthday = source.resolve("birthday.jpg");
        Files.writeString(beach, "beach-one");
        Files.writeString(birthday, "birthday-one");
        Files.createDirectories(source.resolve("@eaDir"));
        Files.writeString(source.resolve("@eaDir").resolve("thumbnail.jpg"), "ignored");

        ResponseEntity<Map> createdLibrary = restTemplate.exchange(
                "/api/libraries",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("name", "Family Photos")),
                Map.class
        );
        String libraryId = createdLibrary.getBody().get("id").toString();
        ResponseEntity<Map> addedSource = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/roots",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("path", source.toString())),
                Map.class
        );
        String rootId = sourceRows(addedSource).get(0).get("id").toString();

        ResponseEntity<Map> firstScanStart = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/scans",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, null),
                Map.class
        );
        ResponseEntity<Map> firstScan = waitForScan(firstScanStart, cookie);

        Path favorites = Files.createDirectories(source.resolve("favorites"));
        Files.move(beach, favorites.resolve("beach.jpg"));
        Files.writeString(source.resolve("birthday-copy.jpg"), "birthday-one");
        ResponseEntity<Map> secondScanStart = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/scans",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, null),
                Map.class
        );
        ResponseEntity<Map> secondScan = waitForScan(secondScanStart, cookie);

        Files.delete(birthday);
        ResponseEntity<Map> thirdScanStart = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/roots/" + rootId + "/scans",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, null),
                Map.class
        );
        ResponseEntity<Map> thirdScan = waitForScan(thirdScanStart, cookie);

        Files.writeString(birthday, "birthday-one");
        ResponseEntity<Map> fourthScanStart = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/scans",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, null),
                Map.class
        );
        ResponseEntity<Map> fourthScan = waitForScan(fourthScanStart, cookie);
        ResponseEntity<List> scanHistory = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/scans",
                HttpMethod.GET,
                withCookie(cookie),
                List.class
        );
        String latestScanId = firstScanRow(scanHistory).get("id").toString();
        ResponseEntity<Map> scanDetail = restTemplate.exchange(
                "/api/scans/" + latestScanId,
                HttpMethod.GET,
                withCookie(cookie),
                Map.class
        );
        ResponseEntity<Map> tree = restTemplate.exchange(
                "/api/library-tree?libraryId=" + libraryId,
                HttpMethod.GET,
                withCookie(cookie),
                Map.class
        );
        ResponseEntity<Map> assets = restTemplate.exchange(
                "/api/assets?libraryId=" + libraryId + "&q=beach&pageSize=12",
                HttpMethod.GET,
                withCookie(cookie),
                Map.class
        );
        String assetId = firstAsset(assets).get("id").toString();
        ResponseEntity<Map> assetDetail = restTemplate.exchange(
                "/api/assets/" + assetId,
                HttpMethod.GET,
                withCookie(cookie),
                Map.class
        );
        ResponseEntity<byte[]> originalFile = restTemplate.exchange(
                "/api/assets/" + assetId + "/file",
                HttpMethod.GET,
                withCookie(cookie),
                byte[].class
        );
        ResponseEntity<Map> backfill = restTemplate.exchange(
                "/api/assets/metadata/backfill",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, null),
                Map.class
        );

        assertThat(firstScanStart.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(firstScan.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstScan.getBody())
                .containsEntry("status", "completed")
                .containsEntry("scannedFileCount", 2)
                .containsEntry("addedCount", 2);
        assertThat(secondScanStart.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(secondScan.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondScan.getBody())
                .containsEntry("scannedFileCount", 3)
                .containsEntry("unchangedCount", 1)
                .containsEntry("movedCount", 1)
                .containsEntry("duplicateCount", 1)
                .containsEntry("missingCount", 0);
        assertThat(thirdScanStart.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(thirdScan.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(thirdScan.getBody())
                .containsEntry("scannedFileCount", 2)
                .containsEntry("missingCount", 1);
        assertThat(fourthScanStart.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(fourthScan.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fourthScan.getBody())
                .containsEntry("scannedFileCount", 3)
                .containsEntry("reappearedCount", 1);
        assertThat(scanHistory.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scanHistory.getBody()).hasSize(4);
        assertThat(scanDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scanDetail.getBody()).containsEntry("id", latestScanId);
        assertThat(tree.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstTreeRoot(tree)).containsEntry("libraryId", libraryId);
        assertThat(firstTreeRoot(tree)).containsKey("children");
        assertThat(assets.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(assets.getBody()).containsEntry("totalCount", 1);
        assertThat(firstAsset(assets)).containsEntry("fileName", "beach.jpg");
        assertThat(firstAsset(assets)).containsEntry("duplicateCount", 1);
        assertThat(firstAsset(assets)).containsEntry("previewable", true);
        assertThat(assetDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(assetDetail.getBody()).containsEntry("id", assetId);
        assertThat(assetDetail.getBody()).containsEntry("availability", "available");
        assertThat(assetDetail.getBody()).containsEntry("identityStatus", "confirmed");
        assertThat(assetFiles(assetDetail)).isNotEmpty();
        assertThat(originalFile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(originalFile.getHeaders().getContentType().toString()).isEqualTo("image/jpeg");
        assertThat(originalFile.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(originalFile.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        assertThat(originalFile.getBody()).isNotEmpty();
        assertThat(backfill.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(backfill.getBody()).containsKey("processedCount");
    }

    @Test
    void activeScansEndpointShowsRunningManualScan() throws Exception {
        ResponseEntity<Map> admin = createFirstAdmin();
        String cookie = cookiePair(admin);
        String csrfToken = csrfToken(admin);
        Path source = Files.createDirectory(tempDir.resolve("active-scan"));
        Files.writeString(source.resolve("photo.jpg"), "photo");

        ResponseEntity<Map> createdLibrary = restTemplate.exchange(
                "/api/libraries",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("name", "Active Scan Library")),
                Map.class
        );
        String libraryId = createdLibrary.getBody().get("id").toString();
        restTemplate.exchange(
                "/api/libraries/" + libraryId + "/roots",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("path", source.toString())),
                Map.class
        );

        ResponseEntity<Map> startedScan = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/scans",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, null),
                Map.class
        );
        ResponseEntity<List> activeScans = restTemplate.exchange(
                "/api/scans/active",
                HttpMethod.GET,
                withCookie(cookie),
                List.class
        );
        ResponseEntity<Map> completedScan = waitForScan(startedScan, cookie);
        ResponseEntity<List> activeAfterComplete = restTemplate.exchange(
                "/api/scans/active",
                HttpMethod.GET,
                withCookie(cookie),
                List.class
        );

        assertThat(startedScan.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(activeScans.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activeScans.getBody()).isNotNull();
        assertThat(completedScan.getBody()).containsEntry("status", "completed");
        assertThat(activeAfterComplete.getBody()).isEmpty();
    }

    @Test
    void repeatScanWithoutChangesLeavesFilesUnchanged() throws Exception {
        ResponseEntity<Map> admin = createFirstAdmin();
        String cookie = cookiePair(admin);
        String csrfToken = csrfToken(admin);
        Path source = Files.createDirectory(tempDir.resolve("repeat-scan"));
        Files.writeString(source.resolve("one.jpg"), "one");
        Files.writeString(source.resolve("two.jpg"), "two");

        ResponseEntity<Map> createdLibrary = restTemplate.exchange(
                "/api/libraries",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("name", "Repeat Scan Library")),
                Map.class
        );
        String libraryId = createdLibrary.getBody().get("id").toString();
        restTemplate.exchange(
                "/api/libraries/" + libraryId + "/roots",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("path", source.toString())),
                Map.class
        );

        ResponseEntity<Map> firstScanStart = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/scans",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, null),
                Map.class
        );
        ResponseEntity<Map> firstScan = waitForScan(firstScanStart, cookie);
        ResponseEntity<Map> secondScanStart = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/scans",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, null),
                Map.class
        );
        ResponseEntity<Map> secondScan = waitForScan(secondScanStart, cookie);

        assertThat(firstScan.getBody()).containsEntry("addedCount", 2);
        assertThat(secondScan.getBody())
                .containsEntry("status", "completed")
                .containsEntry("scannedFileCount", 2)
                .containsEntry("unchangedCount", 2)
                .containsEntry("addedCount", 0);
    }

    @Test
    void libraryTreeShowsNestedFoldersRelativeToSourceRoot() throws Exception {
        ResponseEntity<Map> admin = createFirstAdmin();
        String cookie = cookiePair(admin);
        String csrfToken = csrfToken(admin);
        Path source = Files.createDirectory(tempDir.resolve("pixierge-library"));
        Path japanDay = Files.createDirectories(source.resolve("Japan 2026").resolve("31 March 2026"));
        Path janeen = Files.createDirectories(source.resolve("janeen"));
        Files.writeString(japanDay.resolve("IMG_1.jpeg"), "img-one");
        Files.writeString(janeen.resolve("file_1.jpg"), "img-two");

        ResponseEntity<Map> createdLibrary = restTemplate.exchange(
                "/api/libraries",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("name", "Family Photos")),
                Map.class
        );
        String libraryId = createdLibrary.getBody().get("id").toString();
        restTemplate.exchange(
                "/api/libraries/" + libraryId + "/roots",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("path", source.toString())),
                Map.class
        );
        ResponseEntity<Map> scanStart = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/scans",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, null),
                Map.class
        );
        ResponseEntity<Map> scan = waitForScan(scanStart, cookie);
        ResponseEntity<Map> tree = restTemplate.exchange(
                "/api/library-tree?libraryId=" + libraryId,
                HttpMethod.GET,
                withCookie(cookie),
                Map.class
        );

        assertThat(scan.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scan.getBody()).containsEntry("addedCount", 2);
        assertThat(tree.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> roots = treeRoots(tree);
        assertThat(roots).extracting(node -> node.get("name")).containsExactlyInAnyOrder("Japan 2026", "janeen");

        Map<String, Object> japan = roots.stream()
                .filter(node -> "Japan 2026".equals(node.get("name")))
                .findFirst()
                .orElseThrow();
        List<Map<String, Object>> japanChildren = treeChildren(japan);
        assertThat(japanChildren).hasSize(1);
        assertThat(japanChildren.get(0)).containsEntry("name", "31 March 2026");
        assertThat(japanChildren.get(0)).containsEntry("assetCount", 1);
    }

    @Test
    void renameFolderMovesDirectoryAndRewritesIndexedPaths() throws Exception {
        ResponseEntity<Map> admin = createFirstAdmin();
        String cookie = cookiePair(admin);
        String csrfToken = csrfToken(admin);
        Path source = Files.createDirectory(tempDir.resolve("rename-library"));
        Path events = Files.createDirectories(source.resolve("Events"));
        Path nested = Files.createDirectories(events.resolve("2017-09-01"));
        Files.writeString(nested.resolve("IMG_1.jpeg"), "img-one");

        ResponseEntity<Map> createdLibrary = restTemplate.exchange(
                "/api/libraries",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("name", "Family Photos")),
                Map.class
        );
        String libraryId = createdLibrary.getBody().get("id").toString();
        restTemplate.exchange(
                "/api/libraries/" + libraryId + "/roots",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("path", source.toString())),
                Map.class
        );
        ResponseEntity<Map> scanStart = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/scans",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, null),
                Map.class
        );
        waitForScan(scanStart, cookie);

        ResponseEntity<Map> treeBefore = restTemplate.exchange(
                "/api/library-tree?libraryId=" + libraryId,
                HttpMethod.GET,
                withCookie(cookie),
                Map.class
        );
        Map<String, Object> eventsNode = treeRoots(treeBefore).stream()
                .filter(node -> "Events".equals(node.get("name")))
                .findFirst()
                .orElseThrow();
        String eventsPath = eventsNode.get("path").toString();

        ResponseEntity<Map> renamed = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/folders/rename",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("path", eventsPath, "name", "Parties")),
                Map.class
        );

        assertThat(renamed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(renamed.getBody()).containsEntry("name", "Parties");
        assertThat(Files.exists(source.resolve("Events"))).isFalse();
        assertThat(Files.exists(source.resolve("Parties").resolve("2017-09-01").resolve("IMG_1.jpeg"))).isTrue();

        ResponseEntity<Map> treeAfter = restTemplate.exchange(
                "/api/library-tree?libraryId=" + libraryId,
                HttpMethod.GET,
                withCookie(cookie),
                Map.class
        );
        List<Map<String, Object>> roots = treeRoots(treeAfter);
        assertThat(roots).extracting(node -> node.get("name")).containsExactly("Parties");
        Map<String, Object> parties = roots.get(0);
        assertThat(treeChildren(parties)).extracting(node -> node.get("name")).containsExactly("2017-09-01");

        ResponseEntity<Map> assets = restTemplate.exchange(
                "/api/assets?libraryId=" + libraryId + "&folder=" + parties.get("path") + "&includeDescendants=true",
                HttpMethod.GET,
                withCookie(cookie),
                Map.class
        );
        assertThat(assets.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(assets.getBody()).containsEntry("totalCount", 1);
        assertThat(firstAsset(assets).get("folderPath").toString()).endsWith("/Parties/2017-09-01");
    }

    @Test
    void libraryTreeCountsAssetsAtNestedLibraryRoot() throws Exception {
        ResponseEntity<Map> admin = createFirstAdmin();
        String cookie = cookiePair(admin);
        String csrfToken = csrfToken(admin);
        Path source = Files.createDirectory(tempDir.resolve("pixierge-library"));
        Path janeen = Files.createDirectories(source.resolve("janeen"));
        Files.writeString(janeen.resolve("file_1.jpg"), "img-one");
        Files.writeString(janeen.resolve("file_2.jpg"), "img-two");

        ResponseEntity<Map> famLibrary = restTemplate.exchange(
                "/api/libraries",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("name", "fam")),
                Map.class
        );
        String famLibraryId = famLibrary.getBody().get("id").toString();
        restTemplate.exchange(
                "/api/libraries/" + famLibraryId + "/roots",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("path", source.toString())),
                Map.class
        );

        ResponseEntity<Map> janeenLibrary = restTemplate.exchange(
                "/api/libraries",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("name", "janeen")),
                Map.class
        );
        String janeenLibraryId = janeenLibrary.getBody().get("id").toString();
        restTemplate.exchange(
                "/api/libraries/" + janeenLibraryId + "/roots",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("path", janeen.toString())),
                Map.class
        );

        ResponseEntity<Map> famScanStart = restTemplate.exchange(
                "/api/libraries/" + famLibraryId + "/scans",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, null),
                Map.class
        );
        waitForScan(famScanStart, cookie);
        ResponseEntity<Map> janeenScanStart = restTemplate.exchange(
                "/api/libraries/" + janeenLibraryId + "/scans",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, null),
                Map.class
        );
        waitForScan(janeenScanStart, cookie);

        ResponseEntity<Map> tree = restTemplate.exchange(
                "/api/library-tree",
                HttpMethod.GET,
                withCookie(cookie),
                Map.class
        );

        assertThat(tree.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> roots = treeRoots(tree);
        Map<String, Object> famJaneenNode = roots.stream()
                .filter(node -> famLibraryId.equals(node.get("libraryId")) && "janeen".equals(node.get("name")))
                .findFirst()
                .orElseThrow();
        assertThat(famJaneenNode).containsEntry("assetCount", 2);

        @SuppressWarnings("unchecked")
        Map<String, Object> rootCounts = (Map<String, Object>) tree.getBody().get("libraryRootAssetCounts");
        assertThat(rootCounts).containsEntry(janeenLibraryId, 2);
        assertThat(roots.stream().noneMatch(node -> janeenLibraryId.equals(node.get("libraryId")))).isTrue();
    }

    @Test
    void libraryTreeAssetCountMatchesBrowseTotal() throws Exception {
        ResponseEntity<Map> admin = createFirstAdmin();
        String cookie = cookiePair(admin);
        String csrfToken = csrfToken(admin);
        Path source = Files.createDirectory(tempDir.resolve("duplicate-counts"));
        Path photos = Files.createDirectories(source.resolve("photos"));
        Files.writeString(photos.resolve("photo-a.jpg"), "content-a");
        Files.writeString(photos.resolve("photo-b.jpg"), "content-b");
        Files.writeString(photos.resolve("photo-b-copy.jpg"), "content-b");

        ResponseEntity<Map> createdLibrary = restTemplate.exchange(
                "/api/libraries",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("name", "Duplicates")),
                Map.class
        );
        String libraryId = createdLibrary.getBody().get("id").toString();
        restTemplate.exchange(
                "/api/libraries/" + libraryId + "/roots",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("path", source.toString())),
                Map.class
        );
        ResponseEntity<Map> scanStart = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/scans",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, null),
                Map.class
        );
        waitForScan(scanStart, cookie);

        ResponseEntity<Map> tree = restTemplate.exchange(
                "/api/library-tree?libraryId=" + libraryId,
                HttpMethod.GET,
                withCookie(cookie),
                Map.class
        );
        ResponseEntity<Map> assets = restTemplate.exchange(
                "/api/assets?libraryId=" + libraryId,
                HttpMethod.GET,
                withCookie(cookie),
                Map.class
        );

        assertThat(tree.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(assets.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(assets.getBody()).containsEntry("totalCount", 2);

        @SuppressWarnings("unchecked")
        Map<String, Object> libraryAssetCounts = (Map<String, Object>) tree.getBody().get("libraryAssetCounts");
        assertThat(libraryAssetCounts).containsEntry(libraryId, 2);

        Map<String, Object> photosNode = treeRoots(tree).stream()
                .filter(node -> libraryId.equals(node.get("libraryId")) && "photos".equals(node.get("name")))
                .findFirst()
                .orElseThrow();
        assertThat(photosNode).containsEntry("assetCount", 2);
    }

    @Test
    void thumbnailEndpointsGenerateRevalidateRepairAndPurgeCachedFiles() throws Exception {
        ResponseEntity<Map> admin = createFirstAdmin();
        String cookie = cookiePair(admin);
        String csrfToken = csrfToken(admin);
        Path source = Files.createDirectory(tempDir.resolve("thumbnail-source"));
        createJpeg(source.resolve("photo.jpg"));

        ResponseEntity<Map> createdLibrary = restTemplate.exchange(
                "/api/libraries",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("name", "Thumbnail Library")),
                Map.class
        );
        String libraryId = createdLibrary.getBody().get("id").toString();
        restTemplate.exchange(
                "/api/libraries/" + libraryId + "/roots",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, Map.of("path", source.toString())),
                Map.class
        );
        ResponseEntity<Map> scanStart = restTemplate.exchange(
                "/api/libraries/" + libraryId + "/scans",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, null),
                Map.class
        );
        waitForScan(scanStart, cookie);

        ResponseEntity<Map> assets = restTemplate.exchange(
                "/api/assets?libraryId=" + libraryId,
                HttpMethod.GET,
                withCookie(cookie),
                Map.class
        );
        String assetId = firstAsset(assets).get("id").toString();
        assertThat(firstAsset(assets).get("thumbnailCacheKey")).isNotNull();

        ResponseEntity<byte[]> firstThumbnail = restTemplate.exchange(
                "/api/assets/" + assetId + "/thumbnail",
                HttpMethod.GET,
                withCookie(cookie),
                byte[].class
        );
        ResponseEntity<byte[]> tinyThumbnail = restTemplate.exchange(
                "/api/assets/" + assetId + "/thumbnail?size=tiny",
                HttpMethod.GET,
                withCookie(cookie),
                byte[].class
        );
        ResponseEntity<byte[]> preview = restTemplate.exchange(
                "/api/assets/" + assetId + "/preview",
                HttpMethod.GET,
                withCookie(cookie),
                byte[].class
        );
        String etag = firstThumbnail.getHeaders().getETag();
        assertThat(firstThumbnail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstThumbnail.getHeaders().getContentType().toString()).isEqualTo("image/jpeg");
        assertThat(firstThumbnail.getHeaders().getCacheControl()).contains("private").contains("max-age=86400");
        assertThat(firstThumbnail.getHeaders().getLastModified()).isPositive();
        assertThat(etag).isNotBlank();
        assertThat(tinyThumbnail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tinyThumbnail.getHeaders().getContentType().toString()).isEqualTo("image/jpeg");
        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(preview.getHeaders().getContentType().toString()).isEqualTo("image/jpeg");
        assertThat(preview.getHeaders().getETag()).isNotBlank();
        assertThat(preview.getBody()).isNotEmpty();

        ResponseEntity<byte[]> notModified = restTemplate.exchange(
                "/api/assets/" + assetId + "/thumbnail",
                HttpMethod.GET,
                withCookieAndHeader(cookie, HttpHeaders.IF_NONE_MATCH, etag),
                byte[].class
        );
        assertThat(notModified.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);

        String relativePath = transactionTemplate.execute(status -> queryFactory
                .select(QThumbnails.thumbnails.relativePath)
                .from(QThumbnails.thumbnails)
                .where(QThumbnails.thumbnails.thumbnailType.eq("grid"))
                .fetchOne());
        assertThat(relativePath).isNotBlank();
        Path generated = THUMBNAIL_STORAGE_ROOT.resolve(relativePath);
        assertThat(generated).isRegularFile();

        Files.delete(generated);
        ResponseEntity<byte[]> regenerated = restTemplate.exchange(
                "/api/assets/" + assetId + "/thumbnail",
                HttpMethod.GET,
                withCookie(cookie),
                byte[].class
        );
        assertThat(regenerated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(regenerated.getHeaders().getETag()).isEqualTo(etag);
        assertThat(generated).isRegularFile();

        ResponseEntity<Map> missingCsrf = restTemplate.exchange(
                "/api/admin/thumbnails/rebuild-missing",
                HttpMethod.POST,
                withCookie(cookie),
                Map.class
        );
        assertThat(missingCsrf.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        Files.delete(generated);
        ResponseEntity<Map> rebuild = restTemplate.exchange(
                "/api/admin/thumbnails/rebuild-missing",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, null),
                Map.class
        );
        assertThat(rebuild.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rebuild.getBody()).containsEntry("processedCount", 1).containsEntry("failedCount", 0);
        assertThat(generated).isRegularFile();

        ResponseEntity<byte[]> noSession = restTemplate.getForEntity(
                "/api/assets/" + assetId + "/thumbnail",
                byte[].class
        );
        createStandardUser();
        ResponseEntity<Map> userLogin = login(USER_USERNAME, USER_PASSWORD);
        ResponseEntity<byte[]> unauthorizedUser = restTemplate.exchange(
                "/api/assets/" + assetId + "/thumbnail",
                HttpMethod.GET,
                withCookie(cookiePair(userLogin)),
                byte[].class
        );
        assertThat(noSession.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unauthorizedUser.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        List<String> generatedPaths = transactionTemplate.execute(status -> queryFactory
                .select(QThumbnails.thumbnails.relativePath)
                .from(QThumbnails.thumbnails)
                .fetch());
        assertThat(generatedPaths).hasSize(3);
        transactionTemplate.executeWithoutResult(status -> queryFactory
                .update(QThumbnails.thumbnails)
                .set(QThumbnails.thumbnails.generatorVersion, "legacy-generator")
                .execute());

        ResponseEntity<Map> purge = restTemplate.exchange(
                "/api/admin/thumbnails/purge-stale",
                HttpMethod.POST,
                withCookieAndCsrf(cookie, csrfToken, null),
                Map.class
        );
        assertThat(purge.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(purge.getBody()).containsEntry("processedCount", 3).containsEntry("failedCount", 0);
        assertThat(generatedPaths).allSatisfy(path -> assertThat(THUMBNAIL_STORAGE_ROOT.resolve(path)).doesNotExist());
        Long remainingRows = transactionTemplate.execute(status -> queryFactory
                .select(QThumbnails.thumbnails.id.count())
                .from(QThumbnails.thumbnails)
                .fetchOne());
        assertThat(remainingRows).isZero();
    }

    @Test
    void libraryEndpointsRequireSessionCsrfAndLibraryAdminPermission() {
        ResponseEntity<Map> noSession = restTemplate.getForEntity("/api/libraries", Map.class);
        ResponseEntity<Map> admin = createFirstAdmin();
        String adminCookie = cookiePair(admin);
        createStandardUser();
        ResponseEntity<Map> userLogin = login(USER_USERNAME, USER_PASSWORD);
        String userCookie = cookiePair(userLogin);
        String userCsrf = csrfToken(userLogin);

        ResponseEntity<Map> missingCsrf = restTemplate.exchange(
                "/api/libraries",
                HttpMethod.POST,
                withCookie(adminCookie, Map.of("name", "Archives")),
                Map.class
        );
        ResponseEntity<Map> userRead = restTemplate.exchange(
                "/api/libraries",
                HttpMethod.GET,
                withCookie(userCookie),
                Map.class
        );
        ResponseEntity<Map> userMutation = restTemplate.exchange(
                "/api/libraries",
                HttpMethod.POST,
                withCookieAndCsrf(userCookie, userCsrf, Map.of("name", "Archives")),
                Map.class
        );

        assertThat(noSession.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(missingCsrf.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(userRead.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(userMutation.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void globalExclusionSettingsSupportAdminManageAndPermissionChecks() {
        ResponseEntity<Map> admin = createFirstAdmin();
        String adminCookie = cookiePair(admin);
        String adminCsrf = csrfToken(admin);
        createStandardUser();
        ResponseEntity<Map> userLogin = login(USER_USERNAME, USER_PASSWORD);
        String userCookie = cookiePair(userLogin);
        String userCsrf = csrfToken(userLogin);

        ResponseEntity<Map[]> initial = restTemplate.exchange(
                "/api/settings/global-exclusion-patterns",
                HttpMethod.GET,
                withCookie(adminCookie),
                Map[].class
        );
        ResponseEntity<Map> created = restTemplate.exchange(
                "/api/settings/global-exclusion-patterns",
                HttpMethod.POST,
                withCookieAndCsrf(adminCookie, adminCsrf, Map.of("pattern", "**/cache/**")),
                Map.class
        );
        ResponseEntity<Map> userRead = restTemplate.exchange(
                "/api/settings/global-exclusion-patterns",
                HttpMethod.GET,
                withCookie(userCookie),
                Map.class
        );
        ResponseEntity<Map> userMutation = restTemplate.exchange(
                "/api/settings/global-exclusion-patterns",
                HttpMethod.POST,
                withCookieAndCsrf(userCookie, userCsrf, Map.of("pattern", "**/private/**")),
                Map.class
        );
        ResponseEntity<Map> duplicate = restTemplate.exchange(
                "/api/settings/global-exclusion-patterns",
                HttpMethod.POST,
                withCookieAndCsrf(adminCookie, adminCsrf, Map.of("pattern", "**/cache/**")),
                Map.class
        );
        ResponseEntity<Map> invalid = restTemplate.exchange(
                "/api/settings/global-exclusion-patterns",
                HttpMethod.POST,
                withCookieAndCsrf(adminCookie, adminCsrf, Map.of("pattern", "../secret/**")),
                Map.class
        );
        ResponseEntity<Void> deleted = restTemplate.exchange(
                "/api/settings/global-exclusion-patterns/" + created.getBody().get("id"),
                HttpMethod.DELETE,
                withCookieAndCsrf(adminCookie, adminCsrf, null),
                Void.class
        );
        ResponseEntity<Void> missingDelete = restTemplate.exchange(
                "/api/settings/global-exclusion-patterns/" + created.getBody().get("id"),
                HttpMethod.DELETE,
                withCookieAndCsrf(adminCookie, adminCsrf, null),
                Void.class
        );

        assertThat(initial.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(initial.getBody()).extracting(pattern -> pattern.get("pattern"))
                .containsAll(SEEDED_GLOBAL_EXCLUSIONS);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("pattern", "**/cache/**");
        assertThat(userRead.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(userMutation.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(missingDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }


    private static Path createThumbnailStorageRoot() {
        try {
            return Files.createTempDirectory("pixierge-thumbnail-integration-");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create thumbnail integration storage", exception);
        }
    }

    private void clearThumbnailStorage() throws IOException {
        if (!Files.exists(THUMBNAIL_STORAGE_ROOT)) {
            Files.createDirectories(THUMBNAIL_STORAGE_ROOT);
            return;
        }
        try (var paths = Files.walk(THUMBNAIL_STORAGE_ROOT)) {
            paths.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(THUMBNAIL_STORAGE_ROOT))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new IllegalStateException("Could not clear thumbnail integration storage", exception);
                        }
                    });
        }
    }

    private void createJpeg(Path path) throws IOException {
        System.setProperty("java.awt.headless", "true");
        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        if (!ImageIO.write(image, "jpg", path.toFile())) {
            throw new IOException("JPEG writer is unavailable");
        }
    }

    private ResponseEntity<Map> createFirstAdmin() {
        return restTemplate.postForEntity("/api/setup/admin", Map.of(
                "username", ADMIN_USERNAME,
                "password", ADMIN_PASSWORD
        ), Map.class);
    }

    private ResponseEntity<Map> login(String username, String password) {
        return restTemplate.postForEntity("/api/auth/login", Map.of(
                "username", username,
                "password", password
        ), Map.class);
    }

    private void createStandardUser() {
        transactionTemplate.executeWithoutResult(status -> {
            UUID userId = userRepository.createUser(USER_USERNAME, passwordEncoder.encode(USER_PASSWORD));
            userRepository.assignRole(userId, USER_ROLE);
        });
    }

    private HttpEntity<Void> withCookie(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Void> withCookieAndHeader(String cookie, String headerName, String headerValue) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        headers.add(headerName, headerValue);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Object> withCookie(String cookie, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Object> withCookieAndCsrf(String cookie, String csrfToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        headers.add(CSRF_HEADER, csrfToken);
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

    private ResponseEntity<Map> waitForScan(ResponseEntity<Map> startedScan, String cookie) throws InterruptedException {
        assertThat(startedScan.getBody()).isNotNull();
        assertThat(startedScan.getStatusCode())
                .as("scan start response body: %s", startedScan.getBody())
                .isEqualTo(HttpStatus.ACCEPTED);
        Object id = startedScan.getBody().get("id");
        assertThat(id)
                .as("scan start response body: %s", startedScan.getBody())
                .isNotNull();
        String scanId = id.toString();
        ResponseEntity<Map> latest = startedScan;

        for (int attempt = 0; attempt < 100; attempt++) {
            latest = restTemplate.exchange(
                    "/api/scans/" + scanId,
                    HttpMethod.GET,
                    withCookie(cookie),
                    Map.class
            );
            assertThat(latest.getStatusCode())
                    .as("scan poll response body: %s", latest.getBody())
                    .isEqualTo(HttpStatus.OK);
            assertThat(latest.getBody()).isNotNull();
            Object status = latest.getBody().get("status");
            assertThat(status)
                    .as("scan poll response body: %s", latest.getBody())
                    .isInstanceOf(String.class);
            if (!"running".equals(status) && !"queued".equals(status)) {
                return latest;
            }
            Thread.sleep(100);
        }

        assertThat(latest.getBody().get("status"))
                .as("scan did not finish before the test timeout: %s", latest.getBody())
                .isNotIn("running", "queued");
        return latest;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> sourceRows(ResponseEntity<Map> response) {
        Object sources = response.getBody().get("sources");
        assertThat(sources).isInstanceOf(List.class);
        return (List<Map<String, Object>>) sources;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstLibrary(ResponseEntity<List> response) {
        Object first = response.getBody().get(0);
        assertThat(first).isInstanceOf(Map.class);
        return (Map<String, Object>) first;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstSource(ResponseEntity<List> response) {
        Object sources = firstLibrary(response).get("sources");
        assertThat(sources).isInstanceOf(List.class);
        return (Map<String, Object>) ((List<?>) sources).get(0);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> exclusionRows(ResponseEntity<Map> response) {
        Object patterns = response.getBody().get("exclusionPatterns");
        assertThat(patterns).isInstanceOf(List.class);
        return (List<Map<String, Object>>) patterns;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstScanRow(ResponseEntity<List> response) {
        Object first = response.getBody().get(0);
        assertThat(first).isInstanceOf(Map.class);
        return (Map<String, Object>) first;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstTreeRoot(ResponseEntity<Map> response) {
        Object roots = response.getBody().get("roots");
        assertThat(roots).isInstanceOf(List.class);
        Object first = ((List<?>) roots).get(0);
        assertThat(first).isInstanceOf(Map.class);
        return (Map<String, Object>) first;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> treeRoots(ResponseEntity<Map> response) {
        Object roots = response.getBody().get("roots");
        assertThat(roots).isInstanceOf(List.class);
        return (List<Map<String, Object>>) roots;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> treeChildren(Map<String, Object> node) {
        Object children = node.get("children");
        assertThat(children).isInstanceOf(List.class);
        return (List<Map<String, Object>>) children;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstAsset(ResponseEntity<Map> response) {
        Object sections = response.getBody().get("sections");
        assertThat(sections).isInstanceOf(List.class);
        Object firstSection = ((List<?>) sections).get(0);
        assertThat(firstSection).isInstanceOf(Map.class);
        Object assets = ((Map<?, ?>) firstSection).get("assets");
        assertThat(assets).isInstanceOf(List.class);
        Object first = ((List<?>) assets).get(0);
        assertThat(first).isInstanceOf(Map.class);
        return (Map<String, Object>) first;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> assetFiles(ResponseEntity<Map> response) {
        Object files = response.getBody().get("files");
        assertThat(files).isInstanceOf(List.class);
        return (List<Map<String, Object>>) files;
    }

}
