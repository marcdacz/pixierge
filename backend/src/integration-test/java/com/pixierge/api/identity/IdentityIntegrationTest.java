package com.pixierge.api.identity;

import com.pixierge.api.db.QPasswordCredentials;
import com.pixierge.api.db.QSessions;
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

import java.util.List;
import java.util.Map;

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

    private ResponseEntity<Map> createFirstAdmin() {
        return restTemplate.postForEntity("/api/setup/admin", adminSetupBody(), Map.class);
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
