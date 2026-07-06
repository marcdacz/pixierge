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

public class QAssets extends RelationalPathBase<QAssets> {

    public static final QAssets assets = new QAssets("assets");

    public final ComparablePath<UUID> id = createComparable("id", UUID.class);
    public final StringPath contentHash = createString("contentHash");
    public final StringPath mediaType = createString("mediaType");
    public final NumberPath<Integer> availableFileCount = createNumber("availableFileCount", Integer.class);
    public final DateTimePath<OffsetDateTime> firstObservedAt = createDateTime("firstObservedAt", OffsetDateTime.class);
    public final DateTimePath<OffsetDateTime> lastObservedAt = createDateTime("lastObservedAt", OffsetDateTime.class);

    public QAssets(String variable) {
        super(QAssets.class, forVariable(variable), null, "assets");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(id, ColumnMetadata.named("id").withIndex(1).ofType(Types.OTHER).notNull());
        addMetadata(contentHash, ColumnMetadata.named("content_hash").withIndex(2).ofType(Types.VARCHAR).notNull());
        addMetadata(mediaType, ColumnMetadata.named("media_type").withIndex(3).ofType(Types.VARCHAR).notNull());
        addMetadata(availableFileCount, ColumnMetadata.named("available_file_count").withIndex(4).ofType(Types.INTEGER).notNull());
        addMetadata(firstObservedAt, ColumnMetadata.named("first_observed_at").withIndex(5).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
        addMetadata(lastObservedAt, ColumnMetadata.named("last_observed_at").withIndex(6).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "assets");
    }
}
