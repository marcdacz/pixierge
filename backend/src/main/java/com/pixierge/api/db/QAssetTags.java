package com.pixierge.api.db;

import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.sql.ColumnMetadata;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SchemaAndTable;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;

public class QAssetTags extends RelationalPathBase<QAssetTags> {

    public static final QAssetTags assetTags = new QAssetTags("asset_tags");

    public final ComparablePath<UUID> tagId = createComparable("tagId", UUID.class);
    public final ComparablePath<UUID> assetId = createComparable("assetId", UUID.class);
    public final ComparablePath<UUID> sourceLibraryId = createComparable("sourceLibraryId", UUID.class);
    public final ComparablePath<UUID> addedBy = createComparable("addedBy", UUID.class);
    public final DateTimePath<OffsetDateTime> createdAt = createDateTime("createdAt", OffsetDateTime.class);

    public QAssetTags(String variable) {
        super(QAssetTags.class, forVariable(variable), null, "asset_tags");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(tagId, ColumnMetadata.named("tag_id").withIndex(1).ofType(Types.OTHER).notNull());
        addMetadata(assetId, ColumnMetadata.named("asset_id").withIndex(2).ofType(Types.OTHER).notNull());
        addMetadata(sourceLibraryId, ColumnMetadata.named("source_library_id").withIndex(3).ofType(Types.OTHER).notNull());
        addMetadata(addedBy, ColumnMetadata.named("added_by").withIndex(4).ofType(Types.OTHER).notNull());
        addMetadata(createdAt, ColumnMetadata.named("created_at").withIndex(5).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "asset_tags");
    }
}
