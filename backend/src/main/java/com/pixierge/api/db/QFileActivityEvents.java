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

public class QFileActivityEvents extends RelationalPathBase<QFileActivityEvents> {
  public static final QFileActivityEvents fileActivityEvents =
      new QFileActivityEvents("file_activity_events");
  public final ComparablePath<UUID> id = createComparable("id", UUID.class);
  public final ComparablePath<UUID> assetId = createComparable("assetId", UUID.class);
  public final StringPath path = createString("path");
  public final StringPath status = createString("status");
  public final DateTimePath<OffsetDateTime> occurredAt =
      createDateTime("occurredAt", OffsetDateTime.class);
  public final StringPath message = createString("message");
  public final NumberPath<Long> durationMs = createNumber("durationMs", Long.class);
  public final ComparablePath<UUID> jobId = createComparable("jobId", UUID.class);
  public final StringPath batchLabel = createString("batchLabel");

  public QFileActivityEvents(String variable) {
    super(QFileActivityEvents.class, forVariable(variable), null, "file_activity_events");
    addMetadata(id, ColumnMetadata.named("id").withIndex(1).ofType(Types.OTHER).notNull());
    addMetadata(assetId, ColumnMetadata.named("asset_id").withIndex(2).ofType(Types.OTHER));
    addMetadata(path, ColumnMetadata.named("path").withIndex(3).ofType(Types.VARCHAR).notNull());
    addMetadata(
        status, ColumnMetadata.named("status").withIndex(4).ofType(Types.VARCHAR).notNull());
    addMetadata(
        occurredAt,
        ColumnMetadata.named("occurred_at")
            .withIndex(5)
            .ofType(Types.TIMESTAMP_WITH_TIMEZONE)
            .notNull());
    addMetadata(message, ColumnMetadata.named("message").withIndex(6).ofType(Types.VARCHAR));
    addMetadata(durationMs, ColumnMetadata.named("duration_ms").withIndex(7).ofType(Types.BIGINT));
    addMetadata(jobId, ColumnMetadata.named("job_id").withIndex(8).ofType(Types.OTHER));
    addMetadata(batchLabel, ColumnMetadata.named("batch_label").withIndex(9).ofType(Types.VARCHAR));
  }

  @Override
  public SchemaAndTable getSchemaAndTable() {
    return new SchemaAndTable(null, "file_activity_events");
  }
}
