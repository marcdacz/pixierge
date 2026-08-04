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

public class QAlbumMembers extends RelationalPathBase<QAlbumMembers> {
  public static final QAlbumMembers albumMembers = new QAlbumMembers("album_members");

  public final ComparablePath<UUID> albumId = createComparable("albumId", UUID.class);
  public final ComparablePath<UUID> userId = createComparable("userId", UUID.class);
  public final StringPath memberRole = createString("memberRole");
  public final DateTimePath<OffsetDateTime> createdAt = createDateTime("createdAt", OffsetDateTime.class);

  public QAlbumMembers(String variable) {
    super(QAlbumMembers.class, forVariable(variable), null, "album_members");
    addMetadata();
  }

  private void addMetadata() {
    addMetadata(albumId, ColumnMetadata.named("album_id").withIndex(1).ofType(Types.OTHER).notNull());
    addMetadata(userId, ColumnMetadata.named("user_id").withIndex(2).ofType(Types.OTHER).notNull());
    addMetadata(memberRole, ColumnMetadata.named("member_role").withIndex(3).ofType(Types.VARCHAR).notNull());
    addMetadata(createdAt, ColumnMetadata.named("created_at").withIndex(4).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
  }

  @Override
  public SchemaAndTable getSchemaAndTable() {
    return new SchemaAndTable(null, "album_members");
  }
}
