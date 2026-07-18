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

public class QScanRuns extends RelationalPathBase<QScanRuns> {

    public static final QScanRuns scanRuns = new QScanRuns("scan_runs");

    public final ComparablePath<UUID> id = createComparable("id", UUID.class);
    public final ComparablePath<UUID> libraryId = createComparable("libraryId", UUID.class);
    public final ComparablePath<UUID> rootId = createComparable("rootId", UUID.class);
    public final ComparablePath<UUID> requestedBy = createComparable("requestedBy", UUID.class);
    public final StringPath status = createString("status");
    public final DateTimePath<OffsetDateTime> startedAt = createDateTime("startedAt", OffsetDateTime.class);
    public final DateTimePath<OffsetDateTime> completedAt = createDateTime("completedAt", OffsetDateTime.class);
    public final DateTimePath<OffsetDateTime> catalogCompletedAt = createDateTime("catalogCompletedAt", OffsetDateTime.class);
    public final NumberPath<Integer> scannedFileCount = createNumber("scannedFileCount", Integer.class);
    public final NumberPath<Integer> addedCount = createNumber("addedCount", Integer.class);
    public final NumberPath<Integer> unchangedCount = createNumber("unchangedCount", Integer.class);
    public final NumberPath<Integer> movedCount = createNumber("movedCount", Integer.class);
    public final NumberPath<Integer> modifiedCount = createNumber("modifiedCount", Integer.class);
    public final NumberPath<Integer> duplicateCount = createNumber("duplicateCount", Integer.class);
    public final NumberPath<Integer> missingCount = createNumber("missingCount", Integer.class);
    public final NumberPath<Integer> reappearedCount = createNumber("reappearedCount", Integer.class);
    public final NumberPath<Integer> errorCount = createNumber("errorCount", Integer.class);

    public QScanRuns(String variable) {
        super(QScanRuns.class, forVariable(variable), null, "scan_runs");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(id, ColumnMetadata.named("id").withIndex(1).ofType(Types.OTHER).notNull());
        addMetadata(libraryId, ColumnMetadata.named("library_id").withIndex(2).ofType(Types.OTHER).notNull());
        addMetadata(rootId, ColumnMetadata.named("root_id").withIndex(3).ofType(Types.OTHER));
        addMetadata(requestedBy, ColumnMetadata.named("requested_by").withIndex(4).ofType(Types.OTHER));
        addMetadata(status, ColumnMetadata.named("status").withIndex(5).ofType(Types.VARCHAR).notNull());
        addMetadata(startedAt, ColumnMetadata.named("started_at").withIndex(6).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
        addMetadata(completedAt, ColumnMetadata.named("completed_at").withIndex(7).ofType(Types.TIMESTAMP_WITH_TIMEZONE));
        addMetadata(scannedFileCount, ColumnMetadata.named("scanned_file_count").withIndex(8).ofType(Types.INTEGER).notNull());
        addMetadata(addedCount, ColumnMetadata.named("added_count").withIndex(9).ofType(Types.INTEGER).notNull());
        addMetadata(unchangedCount, ColumnMetadata.named("unchanged_count").withIndex(10).ofType(Types.INTEGER).notNull());
        addMetadata(movedCount, ColumnMetadata.named("moved_count").withIndex(11).ofType(Types.INTEGER).notNull());
        addMetadata(modifiedCount, ColumnMetadata.named("modified_count").withIndex(12).ofType(Types.INTEGER).notNull());
        addMetadata(duplicateCount, ColumnMetadata.named("duplicate_count").withIndex(13).ofType(Types.INTEGER).notNull());
        addMetadata(missingCount, ColumnMetadata.named("missing_count").withIndex(14).ofType(Types.INTEGER).notNull());
        addMetadata(reappearedCount, ColumnMetadata.named("reappeared_count").withIndex(15).ofType(Types.INTEGER).notNull());
        addMetadata(errorCount, ColumnMetadata.named("error_count").withIndex(16).ofType(Types.INTEGER).notNull());
        addMetadata(catalogCompletedAt, ColumnMetadata.named("catalog_completed_at").withIndex(17).ofType(Types.TIMESTAMP_WITH_TIMEZONE));
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "scan_runs");
    }
}
