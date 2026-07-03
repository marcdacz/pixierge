package com.pixierge.api.db;

import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.ColumnMetadata;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SchemaAndTable;

import java.sql.Types;
import java.time.OffsetDateTime;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;

public class QAppMetadata extends RelationalPathBase<QAppMetadata> {

    public static final QAppMetadata appMetadata = new QAppMetadata("app_metadata");

    public final StringPath metadataKey = createString("metadataKey");
    public final StringPath metadataValue = createString("metadataValue");
    public final DateTimePath<OffsetDateTime> createdAt = createDateTime("createdAt", OffsetDateTime.class);

    public QAppMetadata(String variable) {
        super(QAppMetadata.class, forVariable(variable), null, "app_metadata");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(metadataKey, ColumnMetadata.named("metadata_key").withIndex(1).ofType(Types.VARCHAR).notNull());
        addMetadata(metadataValue, ColumnMetadata.named("metadata_value").withIndex(2).ofType(Types.VARCHAR).notNull());
        addMetadata(createdAt, ColumnMetadata.named("created_at").withIndex(3).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "app_metadata");
    }
}
