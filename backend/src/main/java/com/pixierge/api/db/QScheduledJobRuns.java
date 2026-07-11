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

public class QScheduledJobRuns extends RelationalPathBase<QScheduledJobRuns> {

    public static final QScheduledJobRuns scheduledJobRuns = new QScheduledJobRuns("scheduled_job_runs");

    public final ComparablePath<UUID> id = createComparable("id", UUID.class);
    public final ComparablePath<UUID> jobId = createComparable("jobId", UUID.class);
    public final StringPath triggerSource = createString("triggerSource");
    public final StringPath status = createString("status");
    public final DateTimePath<OffsetDateTime> startedAt = createDateTime("startedAt", OffsetDateTime.class);
    public final DateTimePath<OffsetDateTime> finishedAt = createDateTime("finishedAt", OffsetDateTime.class);
    public final NumberPath<Long> durationMs = createNumber("durationMs", Long.class);
    public final StringPath summaryJson = createString("summaryJson");
    public final StringPath errorMessage = createString("errorMessage");
    public final DateTimePath<OffsetDateTime> createdAt = createDateTime("createdAt", OffsetDateTime.class);

    public QScheduledJobRuns(String variable) {
        super(QScheduledJobRuns.class, forVariable(variable), null, "scheduled_job_runs");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(id, ColumnMetadata.named("id").withIndex(1).ofType(Types.OTHER).notNull());
        addMetadata(jobId, ColumnMetadata.named("job_id").withIndex(2).ofType(Types.OTHER).notNull());
        addMetadata(triggerSource, ColumnMetadata.named("trigger_source").withIndex(3).ofType(Types.VARCHAR).notNull());
        addMetadata(status, ColumnMetadata.named("status").withIndex(4).ofType(Types.VARCHAR).notNull());
        addMetadata(startedAt, ColumnMetadata.named("started_at").withIndex(5).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
        addMetadata(finishedAt, ColumnMetadata.named("finished_at").withIndex(6).ofType(Types.TIMESTAMP_WITH_TIMEZONE));
        addMetadata(durationMs, ColumnMetadata.named("duration_ms").withIndex(7).ofType(Types.BIGINT));
        addMetadata(summaryJson, ColumnMetadata.named("summary_json").withIndex(8).ofType(Types.VARCHAR));
        addMetadata(errorMessage, ColumnMetadata.named("error_message").withIndex(9).ofType(Types.VARCHAR));
        addMetadata(createdAt, ColumnMetadata.named("created_at").withIndex(10).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "scheduled_job_runs");
    }
}
