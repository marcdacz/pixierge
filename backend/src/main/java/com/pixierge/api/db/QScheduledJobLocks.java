package com.pixierge.api.db;

import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.ColumnMetadata;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SchemaAndTable;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;

public class QScheduledJobLocks extends RelationalPathBase<QScheduledJobLocks> {

    public static final QScheduledJobLocks scheduledJobLocks = new QScheduledJobLocks("scheduled_job_locks");

    public final StringPath concurrencyKey = createString("concurrencyKey");
    public final ComparablePath<UUID> jobId = createComparable("jobId", UUID.class);
    public final ComparablePath<UUID> runId = createComparable("runId", UUID.class);
    public final DateTimePath<OffsetDateTime> acquiredAt = createDateTime("acquiredAt", OffsetDateTime.class);

    public QScheduledJobLocks(String variable) {
        super(QScheduledJobLocks.class, forVariable(variable), null, "scheduled_job_locks");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(concurrencyKey, ColumnMetadata.named("concurrency_key").withIndex(1).ofType(Types.VARCHAR).notNull());
        addMetadata(jobId, ColumnMetadata.named("job_id").withIndex(2).ofType(Types.OTHER).notNull());
        addMetadata(runId, ColumnMetadata.named("run_id").withIndex(3).ofType(Types.OTHER).notNull());
        addMetadata(acquiredAt, ColumnMetadata.named("acquired_at").withIndex(4).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "scheduled_job_locks");
    }
}
