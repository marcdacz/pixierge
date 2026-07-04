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

public class QUsers extends RelationalPathBase<QUsers> {

    public static final QUsers users = new QUsers("users");

    public final ComparablePath<UUID> id = createComparable("id", UUID.class);
    public final StringPath username = createString("username");
    public final StringPath status = createString("status");
    public final DateTimePath<OffsetDateTime> createdAt = createDateTime("createdAt", OffsetDateTime.class);
    public final DateTimePath<OffsetDateTime> updatedAt = createDateTime("updatedAt", OffsetDateTime.class);

    public QUsers(String variable) {
        super(QUsers.class, forVariable(variable), null, "users");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(id, ColumnMetadata.named("id").withIndex(1).ofType(Types.OTHER).notNull());
        addMetadata(username, ColumnMetadata.named("username").withIndex(2).ofType(Types.VARCHAR).notNull());
        addMetadata(status, ColumnMetadata.named("status").withIndex(3).ofType(Types.VARCHAR).notNull());
        addMetadata(createdAt, ColumnMetadata.named("created_at").withIndex(4).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
        addMetadata(updatedAt, ColumnMetadata.named("updated_at").withIndex(5).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "users");
    }
}
