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

public class QCatalogSnapshots extends RelationalPathBase<QCatalogSnapshots> {
  public static final QCatalogSnapshots catalogSnapshots =
      new QCatalogSnapshots("catalog_snapshots");
  public final ComparablePath<UUID> id = createComparable("id", UUID.class);
  public final DateTimePath<OffsetDateTime> createdAt =
      createDateTime("createdAt", OffsetDateTime.class);
  public final NumberPath<Long> throughSequence = createNumber("throughSequence", Long.class);
  public final StringPath storagePath = createString("storagePath");
  public final StringPath checksum = createString("checksum");
  public final NumberPath<Long> byteSize = createNumber("byteSize", Long.class);
  public final StringPath status = createString("status");
  public final StringPath failureDetail = createString("failureDetail");

  public QCatalogSnapshots(String variable) {
    super(QCatalogSnapshots.class, forVariable(variable), null, "catalog_snapshots");
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
        throughSequence,
        ColumnMetadata.named("through_sequence").withIndex(3).ofType(Types.BIGINT).notNull());
    addMetadata(
        storagePath,
        ColumnMetadata.named("storage_path").withIndex(4).ofType(Types.VARCHAR).notNull());
    addMetadata(
        checksum, ColumnMetadata.named("checksum").withIndex(5).ofType(Types.VARCHAR).notNull());
    addMetadata(
        byteSize, ColumnMetadata.named("byte_size").withIndex(6).ofType(Types.BIGINT).notNull());
    addMetadata(
        status, ColumnMetadata.named("status").withIndex(7).ofType(Types.VARCHAR).notNull());
    addMetadata(
        failureDetail, ColumnMetadata.named("failure_detail").withIndex(8).ofType(Types.VARCHAR));
  }

  @Override
  public SchemaAndTable getSchemaAndTable() {
    return new SchemaAndTable(null, "catalog_snapshots");
  }
}
