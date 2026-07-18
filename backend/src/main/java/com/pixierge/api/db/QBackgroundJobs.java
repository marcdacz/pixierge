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

public class QBackgroundJobs extends RelationalPathBase<QBackgroundJobs> {

    public static final QBackgroundJobs backgroundJobs = new QBackgroundJobs("background_jobs");

    public final ComparablePath<UUID> id = createComparable("id", UUID.class);
    public final StringPath jobType = createString("jobType");
    public final StringPath payloadJson = createString("payloadJson");
    public final StringPath status = createString("status");
    public final NumberPath<Integer> priority = createNumber("priority", Integer.class);
    public final NumberPath<Integer> attempts = createNumber("attempts", Integer.class);
    public final NumberPath<Integer> maxAttempts = createNumber("maxAttempts", Integer.class);
    public final DateTimePath<OffsetDateTime> nextRunAt = createDateTime("nextRunAt", OffsetDateTime.class);
    public final DateTimePath<OffsetDateTime> leaseUntil = createDateTime("leaseUntil", OffsetDateTime.class);
    public final StringPath lockedBy = createString("lockedBy");
    public final StringPath concurrencyKey = createString("concurrencyKey");
    public final StringPath dedupeKey = createString("dedupeKey");
    public final StringPath progressJson = createString("progressJson");
    public final StringPath lastErrorCode = createString("lastErrorCode");
    public final StringPath lastErrorMessage = createString("lastErrorMessage");
    public final DateTimePath<OffsetDateTime> createdAt = createDateTime("createdAt", OffsetDateTime.class);
    public final DateTimePath<OffsetDateTime> updatedAt = createDateTime("updatedAt", OffsetDateTime.class);
    public final DateTimePath<OffsetDateTime> completedAt = createDateTime("completedAt", OffsetDateTime.class);

    public QBackgroundJobs(String variable) {
        super(QBackgroundJobs.class, forVariable(variable), null, "background_jobs");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(id, ColumnMetadata.named("id").withIndex(1).ofType(Types.OTHER).notNull());
        addMetadata(jobType, ColumnMetadata.named("job_type").withIndex(2).ofType(Types.VARCHAR).notNull());
        addMetadata(payloadJson, ColumnMetadata.named("payload_json").withIndex(3).ofType(Types.VARCHAR).notNull());
        addMetadata(status, ColumnMetadata.named("status").withIndex(4).ofType(Types.VARCHAR).notNull());
        addMetadata(priority, ColumnMetadata.named("priority").withIndex(5).ofType(Types.INTEGER).notNull());
        addMetadata(attempts, ColumnMetadata.named("attempts").withIndex(6).ofType(Types.INTEGER).notNull());
        addMetadata(maxAttempts, ColumnMetadata.named("max_attempts").withIndex(7).ofType(Types.INTEGER).notNull());
        addMetadata(nextRunAt, ColumnMetadata.named("next_run_at").withIndex(8).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
        addMetadata(leaseUntil, ColumnMetadata.named("lease_until").withIndex(9).ofType(Types.TIMESTAMP_WITH_TIMEZONE));
        addMetadata(lockedBy, ColumnMetadata.named("locked_by").withIndex(10).ofType(Types.VARCHAR));
        addMetadata(concurrencyKey, ColumnMetadata.named("concurrency_key").withIndex(11).ofType(Types.VARCHAR).notNull());
        addMetadata(dedupeKey, ColumnMetadata.named("dedupe_key").withIndex(12).ofType(Types.VARCHAR));
        addMetadata(progressJson, ColumnMetadata.named("progress_json").withIndex(13).ofType(Types.VARCHAR));
        addMetadata(lastErrorCode, ColumnMetadata.named("last_error_code").withIndex(14).ofType(Types.VARCHAR));
        addMetadata(lastErrorMessage, ColumnMetadata.named("last_error_message").withIndex(15).ofType(Types.VARCHAR));
        addMetadata(createdAt, ColumnMetadata.named("created_at").withIndex(16).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
        addMetadata(updatedAt, ColumnMetadata.named("updated_at").withIndex(17).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
        addMetadata(completedAt, ColumnMetadata.named("completed_at").withIndex(18).ofType(Types.TIMESTAMP_WITH_TIMEZONE));
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "background_jobs");
    }
}
