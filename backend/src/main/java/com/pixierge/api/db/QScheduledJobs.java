package com.pixierge.api.db;

import com.querydsl.core.types.dsl.BooleanPath;
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

public class QScheduledJobs extends RelationalPathBase<QScheduledJobs> {

    public static final QScheduledJobs scheduledJobs = new QScheduledJobs("scheduled_jobs");

    public final ComparablePath<UUID> id = createComparable("id", UUID.class);
    public final StringPath jobKey = createString("jobKey");
    public final StringPath displayName = createString("displayName");
    public final StringPath description = createString("description");
    public final StringPath ownerType = createString("ownerType");
    public final BooleanPath enabled = createBoolean("enabled");
    public final StringPath cronExpression = createString("cronExpression");
    public final StringPath timezone = createString("timezone");
    public final DateTimePath<OffsetDateTime> nextRunAt = createDateTime("nextRunAt", OffsetDateTime.class);
    public final DateTimePath<OffsetDateTime> lastRunAt = createDateTime("lastRunAt", OffsetDateTime.class);
    public final StringPath lastStatus = createString("lastStatus");
    public final NumberPath<Integer> timeoutSeconds = createNumber("timeoutSeconds", Integer.class);
    public final StringPath concurrencyKey = createString("concurrencyKey");
    public final DateTimePath<OffsetDateTime> createdAt = createDateTime("createdAt", OffsetDateTime.class);
    public final DateTimePath<OffsetDateTime> updatedAt = createDateTime("updatedAt", OffsetDateTime.class);

    public QScheduledJobs(String variable) {
        super(QScheduledJobs.class, forVariable(variable), null, "scheduled_jobs");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(id, ColumnMetadata.named("id").withIndex(1).ofType(Types.OTHER).notNull());
        addMetadata(jobKey, ColumnMetadata.named("job_key").withIndex(2).ofType(Types.VARCHAR).notNull());
        addMetadata(displayName, ColumnMetadata.named("display_name").withIndex(3).ofType(Types.VARCHAR).notNull());
        addMetadata(description, ColumnMetadata.named("description").withIndex(4).ofType(Types.VARCHAR).notNull());
        addMetadata(ownerType, ColumnMetadata.named("owner_type").withIndex(5).ofType(Types.VARCHAR).notNull());
        addMetadata(enabled, ColumnMetadata.named("enabled").withIndex(6).ofType(Types.BOOLEAN).notNull());
        addMetadata(cronExpression, ColumnMetadata.named("cron_expression").withIndex(7).ofType(Types.VARCHAR).notNull());
        addMetadata(timezone, ColumnMetadata.named("timezone").withIndex(8).ofType(Types.VARCHAR).notNull());
        addMetadata(nextRunAt, ColumnMetadata.named("next_run_at").withIndex(9).ofType(Types.TIMESTAMP_WITH_TIMEZONE));
        addMetadata(lastRunAt, ColumnMetadata.named("last_run_at").withIndex(10).ofType(Types.TIMESTAMP_WITH_TIMEZONE));
        addMetadata(lastStatus, ColumnMetadata.named("last_status").withIndex(11).ofType(Types.VARCHAR));
        addMetadata(timeoutSeconds, ColumnMetadata.named("timeout_seconds").withIndex(12).ofType(Types.INTEGER).notNull());
        addMetadata(concurrencyKey, ColumnMetadata.named("concurrency_key").withIndex(13).ofType(Types.VARCHAR).notNull());
        addMetadata(createdAt, ColumnMetadata.named("created_at").withIndex(14).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
        addMetadata(updatedAt, ColumnMetadata.named("updated_at").withIndex(15).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "scheduled_jobs");
    }
}
