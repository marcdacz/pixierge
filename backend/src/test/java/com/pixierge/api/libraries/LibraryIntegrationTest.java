package com.pixierge.api.libraries;

import com.pixierge.api.db.QLibraries;
import com.pixierge.api.db.QLibraryMembers;
import com.pixierge.api.db.QLibraryRoots;
import com.pixierge.api.db.QPasswordCredentials;
import com.pixierge.api.db.QSessions;
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

import java.nio.file.Files;
import java.nio.file.Path;
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

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

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
    void clearData() {
        transactionTemplate.executeWithoutResult(status -> {
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
        assertThat(addedSource.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(addedSource.getBody()).containsEntry("sourceCount", 1);
        assertThat(duplicateSource.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(missingSource.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fileSource.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(staleList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstLibrary(staleList)).containsEntry("unavailableSourceCount", 1);
        assertThat(firstSource(staleList)).containsEntry("available", false);
        assertThat(firstSource(staleList)).containsEntry("unavailableReason", "missing");
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(firstLibrary(afterDelete)).containsEntry("sourceCount", 0);
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

}
