package com.pixierge.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.pixierge.api.db.QCatalogEvents;
import com.pixierge.api.db.QUsers;
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
class AuditRepositoryIntegrationTest {
  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private CatalogRepository repository;
  @Autowired private SQLQueryFactory queryFactory;
  @Autowired private TransactionTemplate transactionTemplate;

  @BeforeEach
  void clearAuditEvents() {
    transactionTemplate.executeWithoutResult(
        status -> {
          queryFactory.delete(QCatalogEvents.catalogEvents).execute();
        });
  }

  @Test
  void filtersPagesAndExpiresAuditEvents() {
    UUID albumId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now();
    transactionTemplate.executeWithoutResult(
        status -> {
          queryFactory
              .insert(QUsers.users)
              .set(QUsers.users.id, actorId)
              .set(QUsers.users.username, "marc")
              .set(QUsers.users.status, "active")
              .set(QUsers.users.createdAt, now)
              .set(QUsers.users.updatedAt, now)
              .execute();
          repository.addEvent(
              UUID.randomUUID(),
              1,
              CatalogEventTypes.ALBUM_CHANGED,
              "album",
              albumId,
              actorId,
              "{\"action\":\"renamed\"}");
          repository.addEvent(
              UUID.randomUUID(),
              1,
              CatalogEventTypes.TAG_CHANGED,
              "tag",
              UUID.randomUUID(),
              UUID.randomUUID(),
              "{\"action\":\"created\"}");
        });

    var filteredEvents =
        transactionTemplate.execute(
            status ->
                repository.auditHistory(
                    0, 10, "album", actorId, now.minusMinutes(1), now.plusMinutes(1)));
    long filteredCount =
        transactionTemplate.execute(
            status -> repository.auditCount("album", actorId, now.minusMinutes(1), now.plusMinutes(1)));
    var replayEvents =
        transactionTemplate.execute(status -> repository.allEventsThrough(Long.MAX_VALUE));
    long deleted =
        transactionTemplate.execute(status -> repository.deleteAuditBefore(now.plusMinutes(1)));
    long remaining =
        transactionTemplate.execute(status -> repository.auditCount(null, null, null, null));

    assertThat(filteredEvents)
        .extracting(
            CatalogEvent::eventType,
            CatalogEvent::aggregateId,
            CatalogEvent::actorUserId,
            CatalogEvent::actorUsername)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                CatalogEventTypes.ALBUM_CHANGED, albumId, actorId, "marc"));
    assertThat(filteredCount).isEqualTo(1);
    assertThat(replayEvents)
        .extracting(CatalogEvent::eventType)
        .containsExactly(CatalogEventTypes.ALBUM_CHANGED, CatalogEventTypes.TAG_CHANGED);
    assertThat(deleted).isEqualTo(2);
    assertThat(remaining).isZero();
  }
}
