package com.pixierge.api.db;

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

import static com.querydsl.core.types.PathMetadataFactory.forVariable;

public class QThumbnails extends RelationalPathBase<QThumbnails> {

    public static final QThumbnails thumbnails = new QThumbnails("thumbnails");

    public final ComparablePath<UUID> id = createComparable("id", UUID.class);
    public final ComparablePath<UUID> assetId = createComparable("assetId", UUID.class);
    public final StringPath contentHash = createString("contentHash");
    public final StringPath thumbnailType = createString("thumbnailType");
    public final NumberPath<Integer> width = createNumber("width", Integer.class);
    public final NumberPath<Integer> height = createNumber("height", Integer.class);
    public final StringPath format = createString("format");
    public final StringPath generatorVersion = createString("generatorVersion");
    public final StringPath configVersion = createString("configVersion");
    public final StringPath cacheKey = createString("cacheKey");
    public final StringPath relativePath = createString("relativePath");
    public final NumberPath<Long> byteSize = createNumber("byteSize", Long.class);
    public final StringPath placeholder = createString("placeholder");
    public final StringPath status = createString("status");
    public final StringPath errorMessage = createString("errorMessage");
    public final DateTimePath<OffsetDateTime> createdAt = createDateTime("createdAt", OffsetDateTime.class);
    public final DateTimePath<OffsetDateTime> updatedAt = createDateTime("updatedAt", OffsetDateTime.class);
    public final DateTimePath<OffsetDateTime> generatedAt = createDateTime("generatedAt", OffsetDateTime.class);

    public QThumbnails(String variable) {
        super(QThumbnails.class, forVariable(variable), null, "thumbnails");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(id, ColumnMetadata.named("id").withIndex(1).ofType(Types.OTHER).notNull());
        addMetadata(assetId, ColumnMetadata.named("asset_id").withIndex(2).ofType(Types.OTHER).notNull());
        addMetadata(contentHash, ColumnMetadata.named("content_hash").withIndex(3).ofType(Types.VARCHAR).notNull());
        addMetadata(thumbnailType, ColumnMetadata.named("thumbnail_type").withIndex(4).ofType(Types.VARCHAR).notNull());
        addMetadata(width, ColumnMetadata.named("width").withIndex(5).ofType(Types.INTEGER).notNull());
        addMetadata(height, ColumnMetadata.named("height").withIndex(6).ofType(Types.INTEGER).notNull());
        addMetadata(format, ColumnMetadata.named("format").withIndex(7).ofType(Types.VARCHAR).notNull());
        addMetadata(generatorVersion, ColumnMetadata.named("generator_version").withIndex(8).ofType(Types.VARCHAR).notNull());
        addMetadata(configVersion, ColumnMetadata.named("config_version").withIndex(9).ofType(Types.VARCHAR).notNull());
        addMetadata(cacheKey, ColumnMetadata.named("cache_key").withIndex(10).ofType(Types.VARCHAR).notNull());
        addMetadata(relativePath, ColumnMetadata.named("relative_path").withIndex(11).ofType(Types.VARCHAR).notNull());
        addMetadata(byteSize, ColumnMetadata.named("byte_size").withIndex(12).ofType(Types.BIGINT).notNull());
        addMetadata(placeholder, ColumnMetadata.named("placeholder").withIndex(13).ofType(Types.VARCHAR));
        addMetadata(status, ColumnMetadata.named("status").withIndex(14).ofType(Types.VARCHAR).notNull());
        addMetadata(errorMessage, ColumnMetadata.named("error_message").withIndex(15).ofType(Types.VARCHAR));
        addMetadata(createdAt, ColumnMetadata.named("created_at").withIndex(16).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
        addMetadata(updatedAt, ColumnMetadata.named("updated_at").withIndex(17).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
        addMetadata(generatedAt, ColumnMetadata.named("generated_at").withIndex(18).ofType(Types.TIMESTAMP_WITH_TIMEZONE));
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "thumbnails");
    }
}
