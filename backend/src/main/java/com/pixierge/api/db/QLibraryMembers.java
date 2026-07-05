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

public class QLibraryMembers extends RelationalPathBase<QLibraryMembers> {

    public static final QLibraryMembers libraryMembers = new QLibraryMembers("library_members");

    public final ComparablePath<UUID> libraryId = createComparable("libraryId", UUID.class);
    public final ComparablePath<UUID> userId = createComparable("userId", UUID.class);
    public final StringPath memberRole = createString("memberRole");
    public final DateTimePath<OffsetDateTime> createdAt = createDateTime("createdAt", OffsetDateTime.class);

    public QLibraryMembers(String variable) {
        super(QLibraryMembers.class, forVariable(variable), null, "library_members");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(libraryId, ColumnMetadata.named("library_id").withIndex(1).ofType(Types.OTHER).notNull());
        addMetadata(userId, ColumnMetadata.named("user_id").withIndex(2).ofType(Types.OTHER).notNull());
        addMetadata(memberRole, ColumnMetadata.named("member_role").withIndex(3).ofType(Types.VARCHAR).notNull());
        addMetadata(createdAt, ColumnMetadata.named("created_at").withIndex(4).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "library_members");
    }
}
