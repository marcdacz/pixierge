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

public class QPasswordCredentials extends RelationalPathBase<QPasswordCredentials> {

    public static final QPasswordCredentials passwordCredentials = new QPasswordCredentials("password_credentials");

    public final ComparablePath<UUID> userId = createComparable("userId", UUID.class);
    public final StringPath passwordHash = createString("passwordHash");
    public final DateTimePath<OffsetDateTime> createdAt = createDateTime("createdAt", OffsetDateTime.class);
    public final DateTimePath<OffsetDateTime> updatedAt = createDateTime("updatedAt", OffsetDateTime.class);

    public QPasswordCredentials(String variable) {
        super(QPasswordCredentials.class, forVariable(variable), null, "password_credentials");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(userId, ColumnMetadata.named("user_id").withIndex(1).ofType(Types.OTHER).notNull());
        addMetadata(passwordHash, ColumnMetadata.named("password_hash").withIndex(2).ofType(Types.VARCHAR).notNull());
        addMetadata(createdAt, ColumnMetadata.named("created_at").withIndex(3).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
        addMetadata(updatedAt, ColumnMetadata.named("updated_at").withIndex(4).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "password_credentials");
    }
}
