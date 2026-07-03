package com.pixierge.api.db;

import com.querydsl.core.types.dsl.BooleanPath;
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

public class QRoles extends RelationalPathBase<QRoles> {

    public static final QRoles roles = new QRoles("roles");

    public final ComparablePath<UUID> id = createComparable("id", UUID.class);
    public final StringPath roleKey = createString("roleKey");
    public final StringPath name = createString("name");
    public final StringPath description = createString("description");
    public final BooleanPath builtIn = createBoolean("builtIn");
    public final DateTimePath<OffsetDateTime> createdAt = createDateTime("createdAt", OffsetDateTime.class);

    public QRoles(String variable) {
        super(QRoles.class, forVariable(variable), null, "roles");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(id, ColumnMetadata.named("id").withIndex(1).ofType(Types.OTHER).notNull());
        addMetadata(roleKey, ColumnMetadata.named("role_key").withIndex(2).ofType(Types.VARCHAR).notNull());
        addMetadata(name, ColumnMetadata.named("name").withIndex(3).ofType(Types.VARCHAR).notNull());
        addMetadata(description, ColumnMetadata.named("description").withIndex(4).ofType(Types.VARCHAR).notNull());
        addMetadata(builtIn, ColumnMetadata.named("built_in").withIndex(5).ofType(Types.BOOLEAN).notNull());
        addMetadata(createdAt, ColumnMetadata.named("created_at").withIndex(6).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "roles");
    }
}
