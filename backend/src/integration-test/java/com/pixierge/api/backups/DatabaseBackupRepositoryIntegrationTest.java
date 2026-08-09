package com.pixierge.api.backups;

import static org.assertj.core.api.Assertions.assertThat;

import com.pixierge.api.db.QDatabaseBackups;
import com.querydsl.sql.SQLQueryFactory;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "pixierge.scheduler.enabled=false")
class DatabaseBackupRepositoryIntegrationTest {
  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private DatabaseBackupRepository repository;
  @Autowired private SQLQueryFactory queryFactory;
  @Autowired private TransactionTemplate transactionTemplate;

  @BeforeEach
  void clearBackups() {
    transactionTemplate.executeWithoutResult(
        status -> queryFactory.delete(QDatabaseBackups.databaseBackups).execute());
  }

  @Test
  void persistsFindsAndPagesDatabaseBackupMetadata() {
    DatabaseBackup older = backup("2026-08-01T00:00:00Z", "older.dump", "completed", null);
    DatabaseBackup newer = backup("2026-08-02T00:00:00Z", "newer.dump", "failed", "pg_dump failed");

    repository.add(older);
    repository.add(newer);

    long count = repository.count();
    var latest = repository.history(0, 1);
    var storedBackup = repository.find(newer.id());
    var missingBackup = repository.find(UUID.randomUUID());

    assertThat(count).isEqualTo(2);
    assertThat(latest)
        .extracting(DatabaseBackup::id, DatabaseBackup::storagePath, DatabaseBackup::status)
        .containsExactly(org.assertj.core.groups.Tuple.tuple(newer.id(), "newer.dump", "failed"));
    assertThat(storedBackup)
        .hasValueSatisfying(
            stored -> {
              assertThat(stored.checksum()).isEqualTo(newer.checksum());
              assertThat(stored.byteSize()).isEqualTo(newer.byteSize());
              assertThat(stored.postgresVersion()).isEqualTo("16.3");
              assertThat(stored.schemaVersion()).isEqualTo("22");
              assertThat(stored.failureDetail()).isEqualTo("pg_dump failed");
            });
    assertThat(missingBackup).isEmpty();
  }

  private DatabaseBackup backup(
      String createdAt, String storagePath, String status, String failureDetail) {
    return new DatabaseBackup(
        UUID.randomUUID(),
        OffsetDateTime.parse(createdAt),
        storagePath,
        "a".repeat(64),
        256,
        "16.3",
        "22",
        status,
        failureDetail);
  }
}
