package com.pixierge.api.db;

import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.sql.ColumnMetadata;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SchemaAndTable;

import java.sql.Types;
import java.util.UUID;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;

public class QUserRoles extends RelationalPathBase<QUserRoles> {

    public static final QUserRoles userRoles = new QUserRoles("user_roles");

    public final ComparablePath<UUID> userId = createComparable("userId", UUID.class);
    public final ComparablePath<UUID> roleId = createComparable("roleId", UUID.class);

    public QUserRoles(String variable) {
        super(QUserRoles.class, forVariable(variable), null, "user_roles");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(userId, ColumnMetadata.named("user_id").withIndex(1).ofType(Types.OTHER).notNull());
        addMetadata(roleId, ColumnMetadata.named("role_id").withIndex(2).ofType(Types.OTHER).notNull());
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "user_roles");
    }
}
