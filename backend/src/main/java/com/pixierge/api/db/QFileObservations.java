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

public class QFileObservations extends RelationalPathBase<QFileObservations> {

    public static final QFileObservations fileObservations = new QFileObservations("file_observations");

    public final ComparablePath<UUID> id = createComparable("id", UUID.class);
    public final ComparablePath<UUID> scanRunId = createComparable("scanRunId", UUID.class);
    public final ComparablePath<UUID> libraryId = createComparable("libraryId", UUID.class);
    public final ComparablePath<UUID> rootId = createComparable("rootId", UUID.class);
    public final ComparablePath<UUID> assetId = createComparable("assetId", UUID.class);
    public final ComparablePath<UUID> assetFileId = createComparable("assetFileId", UUID.class);
    public final StringPath path = createString("path");
    public final StringPath normalizedPath = createString("normalizedPath");
    public final NumberPath<Long> sizeBytes = createNumber("sizeBytes", Long.class);
    public final DateTimePath<OffsetDateTime> modifiedAt = createDateTime("modifiedAt", OffsetDateTime.class);
    public final StringPath partialHash = createString("partialHash");
    public final StringPath contentHash = createString("contentHash");
    public final StringPath result = createString("result");

    public QFileObservations(String variable) {
        super(QFileObservations.class, forVariable(variable), null, "file_observations");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(id, ColumnMetadata.named("id").withIndex(1).ofType(Types.OTHER).notNull());
        addMetadata(scanRunId, ColumnMetadata.named("scan_run_id").withIndex(2).ofType(Types.OTHER).notNull());
        addMetadata(libraryId, ColumnMetadata.named("library_id").withIndex(3).ofType(Types.OTHER).notNull());
        addMetadata(rootId, ColumnMetadata.named("root_id").withIndex(4).ofType(Types.OTHER).notNull());
        addMetadata(assetId, ColumnMetadata.named("asset_id").withIndex(5).ofType(Types.OTHER));
        addMetadata(assetFileId, ColumnMetadata.named("asset_file_id").withIndex(6).ofType(Types.OTHER));
        addMetadata(path, ColumnMetadata.named("path").withIndex(7).ofType(Types.VARCHAR).notNull());
        addMetadata(normalizedPath, ColumnMetadata.named("normalized_path").withIndex(8).ofType(Types.VARCHAR).notNull());
        addMetadata(sizeBytes, ColumnMetadata.named("size_bytes").withIndex(9).ofType(Types.BIGINT));
        addMetadata(modifiedAt, ColumnMetadata.named("modified_at").withIndex(10).ofType(Types.TIMESTAMP_WITH_TIMEZONE));
        addMetadata(partialHash, ColumnMetadata.named("partial_hash").withIndex(11).ofType(Types.VARCHAR));
        addMetadata(contentHash, ColumnMetadata.named("content_hash").withIndex(12).ofType(Types.VARCHAR));
        addMetadata(result, ColumnMetadata.named("result").withIndex(13).ofType(Types.VARCHAR).notNull());
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "file_observations");
    }
}
