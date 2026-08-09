package com.pixierge.api.catalog;

import com.pixierge.api.db.QCatalogEvents;
import com.pixierge.api.db.QCatalogSnapshots;
import com.pixierge.api.db.QUsers;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.sql.SQLQueryFactory;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class CatalogRepository {
  private static final QCatalogEvents EVENTS = QCatalogEvents.catalogEvents;
  private static final QCatalogSnapshots SNAPSHOTS = QCatalogSnapshots.catalogSnapshots;
  private static final QUsers ACTORS = new QUsers("audit_actor");
  private final SQLQueryFactory queryFactory;

  CatalogRepository(SQLQueryFactory queryFactory) {
    this.queryFactory = queryFactory;
  }

  void addEvent(
      UUID id,
      int version,
      String type,
      String aggregateType,
      UUID aggregateId,
      UUID actorId,
      String payload) {
    queryFactory
        .insert(EVENTS)
        .set(EVENTS.eventId, id)
        .set(EVENTS.eventVersion, version)
        .set(EVENTS.eventType, type)
        .set(EVENTS.aggregateType, aggregateType)
        .set(EVENTS.aggregateId, aggregateId)
        .set(EVENTS.actorUserId, actorId)
        .set(EVENTS.payloadJson, Expressions.stringTemplate("cast({0} as jsonb)", payload))
        .set(EVENTS.createdAt, OffsetDateTime.now())
        .execute();
  }

  List<CatalogEvent> allEventsThrough(long sequence) {
    return events(EVENTS.sequence.loe(sequence), 0, Long.MAX_VALUE, true);
  }

  Optional<Long> newestSequence() {
    return Optional.ofNullable(queryFactory.select(EVENTS.sequence.max()).from(EVENTS).fetchOne());
  }

  Optional<Long> newestExportedSequence() {
    return Optional.ofNullable(
        queryFactory
            .select(SNAPSHOTS.throughSequence.max())
            .from(SNAPSHOTS)
            .where(SNAPSHOTS.status.eq("completed"))
            .fetchOne());
  }

  void markExportedThrough(long sequence) {
    // Export state was deliberately retired when catalog events became audit events.
  }

  void addSnapshot(CatalogSnapshot snapshot) {
    queryFactory
        .insert(SNAPSHOTS)
        .set(SNAPSHOTS.id, snapshot.id())
        .set(SNAPSHOTS.createdAt, snapshot.createdAt())
        .set(SNAPSHOTS.throughSequence, snapshot.throughSequence())
        .set(SNAPSHOTS.storagePath, snapshot.storagePath())
        .set(SNAPSHOTS.checksum, snapshot.checksum())
        .set(SNAPSHOTS.byteSize, snapshot.byteSize())
        .set(SNAPSHOTS.status, snapshot.status())
        .set(SNAPSHOTS.failureDetail, snapshot.failureDetail())
        .execute();
  }

  List<CatalogSnapshot> history(int offset, int limit) {
    return queryFactory
        .select(
            SNAPSHOTS.id,
            SNAPSHOTS.createdAt,
            SNAPSHOTS.throughSequence,
            SNAPSHOTS.storagePath,
            SNAPSHOTS.checksum,
            SNAPSHOTS.byteSize,
            SNAPSHOTS.status,
            SNAPSHOTS.failureDetail)
        .from(SNAPSHOTS)
        .orderBy(SNAPSHOTS.createdAt.desc())
        .offset(offset)
        .limit(limit)
        .fetch()
        .stream()
        .map(this::snapshot)
        .toList();
  }

  Optional<CatalogSnapshot> snapshot(UUID id) {
    Tuple row =
        queryFactory
            .select(
                SNAPSHOTS.id,
                SNAPSHOTS.createdAt,
                SNAPSHOTS.throughSequence,
                SNAPSHOTS.storagePath,
                SNAPSHOTS.checksum,
                SNAPSHOTS.byteSize,
                SNAPSHOTS.status,
                SNAPSHOTS.failureDetail)
            .from(SNAPSHOTS)
            .where(SNAPSHOTS.id.eq(id))
            .fetchOne();
    return Optional.ofNullable(row).map(this::snapshot);
  }

  long snapshotCount() {
    Long count = queryFactory.select(SNAPSHOTS.id.count()).from(SNAPSHOTS).fetchOne();
    return count == null ? 0L : count;
  }

  List<CatalogEvent> auditHistory(
      int offset, int limit, String query, UUID actorId, OffsetDateTime from, OffsetDateTime to) {
    var condition = auditCondition(query, actorId, from, to);
    return queryFactory
        .select(
            EVENTS.sequence,
            EVENTS.eventId,
            EVENTS.eventVersion,
            EVENTS.eventType,
            EVENTS.aggregateType,
            EVENTS.aggregateId,
            EVENTS.actorUserId,
            ACTORS.username,
            EVENTS.payloadJson,
            EVENTS.createdAt)
        .from(EVENTS)
        .leftJoin(ACTORS)
        .on(EVENTS.actorUserId.eq(ACTORS.id))
        .where(condition)
        .orderBy(EVENTS.sequence.desc())
        .offset(offset)
        .limit(limit)
        .fetch()
        .stream()
        .map(row -> event(row, row.get(ACTORS.username)))
        .toList();
  }

  long auditCount(String query, UUID actorId, OffsetDateTime from, OffsetDateTime to) {
    Long count =
        queryFactory
            .select(EVENTS.sequence.count())
            .from(EVENTS)
            .where(auditCondition(query, actorId, from, to))
            .fetchOne();
    return count == null ? 0 : count;
  }

  long deleteAuditBefore(OffsetDateTime cutoff) {
    return queryFactory.delete(EVENTS).where(EVENTS.createdAt.lt(cutoff)).execute();
  }

  private List<CatalogEvent> events(com.querydsl.core.types.Predicate condition) {
    return events(condition, 0, Long.MAX_VALUE, false);
  }

  private List<CatalogEvent> events(
      com.querydsl.core.types.Predicate condition, long offset, long limit) {
    return events(condition, offset, limit, false);
  }

  private List<CatalogEvent> events(
      com.querydsl.core.types.Predicate condition, long offset, long limit, boolean ascending) {
    return queryFactory
        .select(
            EVENTS.sequence,
            EVENTS.eventId,
            EVENTS.eventVersion,
            EVENTS.eventType,
            EVENTS.aggregateType,
            EVENTS.aggregateId,
            EVENTS.actorUserId,
            EVENTS.payloadJson,
            EVENTS.createdAt)
        .from(EVENTS)
        .where(condition)
        .orderBy(ascending ? EVENTS.sequence.asc() : EVENTS.sequence.desc())
        .offset(offset)
        .limit(limit)
        .fetch()
        .stream()
        .map(row -> event(row, null))
        .toList();
  }

  private BooleanBuilder auditCondition(
      String query, UUID actorId, OffsetDateTime from, OffsetDateTime to) {
    BooleanBuilder condition = new BooleanBuilder();
    if (query != null && !query.isBlank())
      condition.and(EVENTS.eventType.containsIgnoreCase(query.trim()));
    if (actorId != null) condition.and(EVENTS.actorUserId.eq(actorId));
    if (from != null) condition.and(EVENTS.createdAt.goe(from));
    if (to != null) condition.and(EVENTS.createdAt.loe(to));
    return condition;
  }

  private CatalogEvent event(Tuple row, String actorUsername) {
    return new CatalogEvent(
        row.get(EVENTS.sequence),
        row.get(EVENTS.eventId),
        row.get(EVENTS.eventVersion),
        row.get(EVENTS.eventType),
        row.get(EVENTS.aggregateType),
        row.get(EVENTS.aggregateId),
        row.get(EVENTS.actorUserId),
        actorUsername,
        row.get(EVENTS.payloadJson),
        row.get(EVENTS.createdAt));
  }

  private CatalogSnapshot snapshot(Tuple row) {
    return new CatalogSnapshot(
        row.get(SNAPSHOTS.id),
        row.get(SNAPSHOTS.createdAt),
        row.get(SNAPSHOTS.throughSequence),
        row.get(SNAPSHOTS.storagePath),
        row.get(SNAPSHOTS.checksum),
        row.get(SNAPSHOTS.byteSize),
        row.get(SNAPSHOTS.status),
        row.get(SNAPSHOTS.failureDetail));
  }
}
