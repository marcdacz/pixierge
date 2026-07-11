package com.pixierge.api.db;

import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.sql.ColumnMetadata;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SchemaAndTable;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;

public class QAlbumItems extends RelationalPathBase<QAlbumItems> {

    public static final QAlbumItems albumItems = new QAlbumItems("album_items");

    public final ComparablePath<UUID> albumId = createComparable("albumId", UUID.class);
    public final ComparablePath<UUID> assetId = createComparable("assetId", UUID.class);
    public final ComparablePath<UUID> sourceLibraryId = createComparable("sourceLibraryId", UUID.class);
    public final NumberPath<Integer> position = createNumber("position", Integer.class);
    public final ComparablePath<UUID> addedBy = createComparable("addedBy", UUID.class);
    public final DateTimePath<OffsetDateTime> createdAt = createDateTime("createdAt", OffsetDateTime.class);
    public final DateTimePath<OffsetDateTime> updatedAt = createDateTime("updatedAt", OffsetDateTime.class);

    public QAlbumItems(String variable) {
        super(QAlbumItems.class, forVariable(variable), null, "album_items");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(albumId, ColumnMetadata.named("album_id").withIndex(1).ofType(Types.OTHER).notNull());
        addMetadata(assetId, ColumnMetadata.named("asset_id").withIndex(2).ofType(Types.OTHER).notNull());
        addMetadata(sourceLibraryId, ColumnMetadata.named("source_library_id").withIndex(3).ofType(Types.OTHER).notNull());
        addMetadata(position, ColumnMetadata.named("position").withIndex(4).ofType(Types.INTEGER).notNull());
        addMetadata(addedBy, ColumnMetadata.named("added_by").withIndex(5).ofType(Types.OTHER).notNull());
        addMetadata(createdAt, ColumnMetadata.named("created_at").withIndex(6).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
        addMetadata(updatedAt, ColumnMetadata.named("updated_at").withIndex(7).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "album_items");
    }
}
