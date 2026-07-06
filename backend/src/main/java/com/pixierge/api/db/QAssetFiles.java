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

public class QAssetFiles extends RelationalPathBase<QAssetFiles> {

    public static final QAssetFiles assetFiles = new QAssetFiles("asset_files");

    public final ComparablePath<UUID> id = createComparable("id", UUID.class);
    public final ComparablePath<UUID> assetId = createComparable("assetId", UUID.class);
    public final ComparablePath<UUID> libraryId = createComparable("libraryId", UUID.class);
    public final ComparablePath<UUID> rootId = createComparable("rootId", UUID.class);
    public final StringPath path = createString("path");
    public final StringPath normalizedPath = createString("normalizedPath");
    public final StringPath fileName = createString("fileName");
    public final NumberPath<Long> sizeBytes = createNumber("sizeBytes", Long.class);
    public final DateTimePath<OffsetDateTime> modifiedAt = createDateTime("modifiedAt", OffsetDateTime.class);
    public final StringPath contentHash = createString("contentHash");
    public final StringPath status = createString("status");
    public final DateTimePath<OffsetDateTime> firstObservedAt = createDateTime("firstObservedAt", OffsetDateTime.class);
    public final DateTimePath<OffsetDateTime> lastObservedAt = createDateTime("lastObservedAt", OffsetDateTime.class);
    public final ComparablePath<UUID> lastSeenScanRunId = createComparable("lastSeenScanRunId", UUID.class);
    public final ComparablePath<UUID> replacedByFileId = createComparable("replacedByFileId", UUID.class);

    public QAssetFiles(String variable) {
        super(QAssetFiles.class, forVariable(variable), null, "asset_files");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(id, ColumnMetadata.named("id").withIndex(1).ofType(Types.OTHER).notNull());
        addMetadata(assetId, ColumnMetadata.named("asset_id").withIndex(2).ofType(Types.OTHER).notNull());
        addMetadata(libraryId, ColumnMetadata.named("library_id").withIndex(3).ofType(Types.OTHER).notNull());
        addMetadata(rootId, ColumnMetadata.named("root_id").withIndex(4).ofType(Types.OTHER).notNull());
        addMetadata(path, ColumnMetadata.named("path").withIndex(5).ofType(Types.VARCHAR).notNull());
        addMetadata(normalizedPath, ColumnMetadata.named("normalized_path").withIndex(6).ofType(Types.VARCHAR).notNull());
        addMetadata(fileName, ColumnMetadata.named("file_name").withIndex(7).ofType(Types.VARCHAR).notNull());
        addMetadata(sizeBytes, ColumnMetadata.named("size_bytes").withIndex(8).ofType(Types.BIGINT).notNull());
        addMetadata(modifiedAt, ColumnMetadata.named("modified_at").withIndex(9).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
        addMetadata(contentHash, ColumnMetadata.named("content_hash").withIndex(10).ofType(Types.VARCHAR).notNull());
        addMetadata(status, ColumnMetadata.named("status").withIndex(11).ofType(Types.VARCHAR).notNull());
        addMetadata(firstObservedAt, ColumnMetadata.named("first_observed_at").withIndex(12).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
        addMetadata(lastObservedAt, ColumnMetadata.named("last_observed_at").withIndex(13).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
        addMetadata(lastSeenScanRunId, ColumnMetadata.named("last_seen_scan_run_id").withIndex(14).ofType(Types.OTHER));
        addMetadata(replacedByFileId, ColumnMetadata.named("replaced_by_file_id").withIndex(15).ofType(Types.OTHER));
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "asset_files");
    }
}
