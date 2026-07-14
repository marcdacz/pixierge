package com.pixierge.api;

import com.pixierge.api.db.QAppMetadata;
import com.querydsl.sql.SQLQueryFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PixiergeApiApplicationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SQLQueryFactory queryFactory;

    @Test
    @Transactional(readOnly = true)
    void runsMigrationsAndReportsReadyHealth() {
        QAppMetadata appMetadata = QAppMetadata.appMetadata;
        String marker = queryFactory
                .select(appMetadata.metadataValue)
                .from(appMetadata)
                .where(appMetadata.metadataKey.eq("schema_marker"))
                .fetchOne();

        var response = restTemplate.getForEntity("/api/health", Map.class);

        assertThat(marker).isEqualTo("baseline");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "ok");
        assertThat(response.getBody()).containsEntry("database", "ready");
        assertThat(response.getBody()).containsEntry("app", "pixierge-api");
    }
}
