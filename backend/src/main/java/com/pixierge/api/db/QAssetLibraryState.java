package com.pixierge.api.db;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;

import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.ColumnMetadata;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SchemaAndTable;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.UUID;

public class QAssetLibraryState extends RelationalPathBase<QAssetLibraryState> {
  public static final QAssetLibraryState assetLibraryState =
      new QAssetLibraryState("asset_library_state");

  public final ComparablePath<UUID> assetId = createComparable("assetId", UUID.class);
  public final ComparablePath<UUID> libraryId = createComparable("libraryId", UUID.class);
  public final StringPath privacy = createString("privacy");
  public final ComparablePath<UUID> updatedBy = createComparable("updatedBy", UUID.class);
  public final DateTimePath<OffsetDateTime> updatedAt =
      createDateTime("updatedAt", OffsetDateTime.class);

  public QAssetLibraryState(String variable) {
    super(QAssetLibraryState.class, forVariable(variable), null, "asset_library_state");
    addMetadata();
  }

  private void addMetadata() {
    addMetadata(
        assetId, ColumnMetadata.named("asset_id").withIndex(1).ofType(Types.OTHER).notNull());
    addMetadata(
        libraryId, ColumnMetadata.named("library_id").withIndex(2).ofType(Types.OTHER).notNull());
    addMetadata(
        privacy, ColumnMetadata.named("privacy").withIndex(3).ofType(Types.VARCHAR).notNull());
    addMetadata(
        updatedBy, ColumnMetadata.named("updated_by").withIndex(4).ofType(Types.OTHER).notNull());
    addMetadata(
        updatedAt,
        ColumnMetadata.named("updated_at")
            .withIndex(5)
            .ofType(Types.TIMESTAMP_WITH_TIMEZONE)
            .notNull());
  }

  @Override
  public SchemaAndTable getSchemaAndTable() {
    return new SchemaAndTable(null, "asset_library_state");
  }
}
