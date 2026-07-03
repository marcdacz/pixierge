package com.pixierge.api.db;

import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.sql.ColumnMetadata;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SchemaAndTable;

import java.sql.Types;
import java.util.UUID;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;

public class QRolePermissions extends RelationalPathBase<QRolePermissions> {

    public static final QRolePermissions rolePermissions = new QRolePermissions("role_permissions");

    public final ComparablePath<UUID> roleId = createComparable("roleId", UUID.class);
    public final ComparablePath<UUID> permissionId = createComparable("permissionId", UUID.class);

    public QRolePermissions(String variable) {
        super(QRolePermissions.class, forVariable(variable), null, "role_permissions");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(roleId, ColumnMetadata.named("role_id").withIndex(1).ofType(Types.OTHER).notNull());
        addMetadata(permissionId, ColumnMetadata.named("permission_id").withIndex(2).ofType(Types.OTHER).notNull());
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "role_permissions");
    }
}
