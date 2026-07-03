package com.pixierge.api.db;

import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.ColumnMetadata;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SchemaAndTable;

import java.sql.Types;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;

public class QSetupLocks extends RelationalPathBase<QSetupLocks> {

    public static final QSetupLocks setupLocks = new QSetupLocks("setup_locks");

    public final StringPath lockKey = createString("lockKey");

    public QSetupLocks(String variable) {
        super(QSetupLocks.class, forVariable(variable), null, "setup_locks");
        addMetadata(lockKey, ColumnMetadata.named("lock_key").withIndex(1).ofType(Types.VARCHAR).notNull());
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "setup_locks");
    }
}
