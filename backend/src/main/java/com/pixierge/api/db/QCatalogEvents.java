package com.pixierge.api.db;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;

import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.ColumnMetadata;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SchemaAndTable;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.UUID;

public class QCatalogEvents extends RelationalPathBase<QCatalogEvents> {
  public static final QCatalogEvents catalogEvents = new QCatalogEvents("catalog_events");
  public final NumberPath<Long> sequence = createNumber("sequence", Long.class);
  public final ComparablePath<UUID> eventId = createComparable("eventId", UUID.class);
  public final NumberPath<Integer> eventVersion = createNumber("eventVersion", Integer.class);
  public final StringPath eventType = createString("eventType");
  public final StringPath aggregateType = createString("aggregateType");
  public final ComparablePath<UUID> aggregateId = createComparable("aggregateId", UUID.class);
  public final ComparablePath<UUID> actorUserId = createComparable("actorUserId", UUID.class);
  public final StringPath payloadJson = createString("payloadJson");
  public final DateTimePath<OffsetDateTime> createdAt =
      createDateTime("createdAt", OffsetDateTime.class);
  public final DateTimePath<OffsetDateTime> exportedAt =
      createDateTime("exportedAt", OffsetDateTime.class);

  public QCatalogEvents(String variable) {
    super(QCatalogEvents.class, forVariable(variable), null, "audit_events");
    addMetadata();
  }

  private void addMetadata() {
    addMetadata(
        sequence, ColumnMetadata.named("sequence").withIndex(1).ofType(Types.BIGINT).notNull());
    addMetadata(
        eventId, ColumnMetadata.named("event_id").withIndex(2).ofType(Types.OTHER).notNull());
    addMetadata(
        eventVersion,
        ColumnMetadata.named("event_version").withIndex(3).ofType(Types.INTEGER).notNull());
    addMetadata(
        eventType, ColumnMetadata.named("event_type").withIndex(4).ofType(Types.VARCHAR).notNull());
    addMetadata(
        aggregateType,
        ColumnMetadata.named("aggregate_type").withIndex(5).ofType(Types.VARCHAR).notNull());
    addMetadata(
        aggregateId,
        ColumnMetadata.named("aggregate_id").withIndex(6).ofType(Types.OTHER).notNull());
    addMetadata(
        actorUserId, ColumnMetadata.named("actor_user_id").withIndex(7).ofType(Types.OTHER));
    addMetadata(
        payloadJson,
        ColumnMetadata.named("payload_json").withIndex(8).ofType(Types.OTHER).notNull());
    addMetadata(
        createdAt,
        ColumnMetadata.named("created_at")
            .withIndex(9)
            .ofType(Types.TIMESTAMP_WITH_TIMEZONE)
            .notNull());
    addMetadata(
        exportedAt,
        ColumnMetadata.named("exported_at").withIndex(10).ofType(Types.TIMESTAMP_WITH_TIMEZONE));
  }

  @Override
  public SchemaAndTable getSchemaAndTable() {
    return new SchemaAndTable(null, "audit_events");
  }
}
