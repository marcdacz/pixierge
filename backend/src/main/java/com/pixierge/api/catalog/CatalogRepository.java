package com.pixierge.api.catalog;

import com.pixierge.api.db.QCatalogEvents;
import com.pixierge.api.db.QCatalogSnapshots;
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
    return events(EVENTS.sequence.loe(sequence));
  }

  Optional<Long> newestSequence() {
    return Optional.ofNullable(queryFactory.select(EVENTS.sequence.max()).from(EVENTS).fetchOne());
  }

  Optional<Long> newestExportedSequence() {
    return Optional.ofNullable(
        queryFactory
            .select(EVENTS.sequence.max())
            .from(EVENTS)
            .where(EVENTS.exportedAt.isNotNull())
            .fetchOne());
  }

  void markExportedThrough(long sequence) {
    queryFactory
        .update(EVENTS)
        .set(EVENTS.exportedAt, OffsetDateTime.now())
        .where(EVENTS.sequence.loe(sequence).and(EVENTS.exportedAt.isNull()))
        .execute();
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

  private List<CatalogEvent> events(com.querydsl.core.types.Predicate condition) {
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
        .orderBy(EVENTS.sequence.asc())
        .fetch()
        .stream()
        .map(this::event)
        .toList();
  }

  private CatalogEvent event(Tuple row) {
    return new CatalogEvent(
        row.get(EVENTS.sequence),
        row.get(EVENTS.eventId),
        row.get(EVENTS.eventVersion),
        row.get(EVENTS.eventType),
        row.get(EVENTS.aggregateType),
        row.get(EVENTS.aggregateId),
        row.get(EVENTS.actorUserId),
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
