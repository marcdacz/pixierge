package com.pixierge.api.db;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;

import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.sql.ColumnMetadata;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SchemaAndTable;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.UUID;

public class QAlbumItemShareApprovals extends RelationalPathBase<QAlbumItemShareApprovals> {
  public static final QAlbumItemShareApprovals albumItemShareApprovals =
      new QAlbumItemShareApprovals("album_item_share_approvals");

  public final ComparablePath<UUID> albumId = createComparable("albumId", UUID.class);
  public final ComparablePath<UUID> assetId = createComparable("assetId", UUID.class);
  public final ComparablePath<UUID> recipientUserId =
      createComparable("recipientUserId", UUID.class);
  public final ComparablePath<UUID> sourceLibraryId =
      createComparable("sourceLibraryId", UUID.class);
  public final ComparablePath<UUID> approvedBy = createComparable("approvedBy", UUID.class);
  public final DateTimePath<OffsetDateTime> approvedAt =
      createDateTime("approvedAt", OffsetDateTime.class);

  public QAlbumItemShareApprovals(String variable) {
    super(
        QAlbumItemShareApprovals.class, forVariable(variable), null, "album_item_share_approvals");
    addMetadata();
  }

  private void addMetadata() {
    addMetadata(
        albumId, ColumnMetadata.named("album_id").withIndex(1).ofType(Types.OTHER).notNull());
    addMetadata(
        assetId, ColumnMetadata.named("asset_id").withIndex(2).ofType(Types.OTHER).notNull());
    addMetadata(
        recipientUserId,
        ColumnMetadata.named("recipient_user_id").withIndex(3).ofType(Types.OTHER).notNull());
    addMetadata(
        sourceLibraryId,
        ColumnMetadata.named("source_library_id").withIndex(4).ofType(Types.OTHER).notNull());
    addMetadata(
        approvedBy, ColumnMetadata.named("approved_by").withIndex(5).ofType(Types.OTHER).notNull());
    addMetadata(
        approvedAt,
        ColumnMetadata.named("approved_at")
            .withIndex(6)
            .ofType(Types.TIMESTAMP_WITH_TIMEZONE)
            .notNull());
  }

  @Override
  public SchemaAndTable getSchemaAndTable() {
    return new SchemaAndTable(null, "album_item_share_approvals");
  }
}
