package com.pixierge.api.db;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;

import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.SimplePath;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.ColumnMetadata;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SchemaAndTable;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.UUID;

public class QDatabaseBackups extends RelationalPathBase<QDatabaseBackups> {
  public static final QDatabaseBackups databaseBackups = new QDatabaseBackups("database_backups");
  public final SimplePath<UUID> id = createSimple("id", UUID.class);
  public final DateTimePath<OffsetDateTime> createdAt =
      createDateTime("createdAt", OffsetDateTime.class);
  public final StringPath storagePath = createString("storagePath");
  public final StringPath checksum = createString("checksum");
  public final NumberPath<Long> byteSize = createNumber("byteSize", Long.class);
  public final StringPath postgresVersion = createString("postgresVersion");
  public final StringPath schemaVersion = createString("schemaVersion");
  public final StringPath status = createString("status");
  public final StringPath failureDetail = createString("failureDetail");

  public QDatabaseBackups(String variable) {
    super(QDatabaseBackups.class, forVariable(variable), null, "database_backups");
    addMetadata();
  }

  public QDatabaseBackups(PathMetadata metadata) {
    super(QDatabaseBackups.class, metadata, null, "database_backups");
    addMetadata();
  }

  private void addMetadata() {
    addMetadata(id, ColumnMetadata.named("id").withIndex(1).ofType(Types.OTHER).notNull());
    addMetadata(
        createdAt,
        ColumnMetadata.named("created_at")
            .withIndex(2)
            .ofType(Types.TIMESTAMP_WITH_TIMEZONE)
            .notNull());
    addMetadata(
        storagePath,
        ColumnMetadata.named("storage_path").withIndex(3).ofType(Types.VARCHAR).notNull());
    addMetadata(
        checksum, ColumnMetadata.named("checksum").withIndex(4).ofType(Types.VARCHAR).notNull());
    addMetadata(
        byteSize, ColumnMetadata.named("byte_size").withIndex(5).ofType(Types.BIGINT).notNull());
    addMetadata(
        postgresVersion,
        ColumnMetadata.named("postgres_version").withIndex(6).ofType(Types.VARCHAR).notNull());
    addMetadata(
        schemaVersion,
        ColumnMetadata.named("schema_version").withIndex(7).ofType(Types.VARCHAR).notNull());
    addMetadata(
        status, ColumnMetadata.named("status").withIndex(8).ofType(Types.VARCHAR).notNull());
    addMetadata(
        failureDetail, ColumnMetadata.named("failure_detail").withIndex(9).ofType(Types.VARCHAR));
  }

  @Override
  public SchemaAndTable getSchemaAndTable() {
    return new SchemaAndTable(null, "database_backups");
  }
}
