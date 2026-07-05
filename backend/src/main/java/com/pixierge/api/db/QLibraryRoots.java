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

public class QLibraryRoots extends RelationalPathBase<QLibraryRoots> {

    public static final QLibraryRoots libraryRoots = new QLibraryRoots("library_roots");

    public final ComparablePath<UUID> id = createComparable("id", UUID.class);
    public final ComparablePath<UUID> libraryId = createComparable("libraryId", UUID.class);
    public final StringPath path = createString("path");
    public final StringPath normalizedPath = createString("normalizedPath");
    public final DateTimePath<OffsetDateTime> createdAt = createDateTime("createdAt", OffsetDateTime.class);
    public final DateTimePath<OffsetDateTime> updatedAt = createDateTime("updatedAt", OffsetDateTime.class);

    public QLibraryRoots(String variable) {
        super(QLibraryRoots.class, forVariable(variable), null, "library_roots");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(id, ColumnMetadata.named("id").withIndex(1).ofType(Types.OTHER).notNull());
        addMetadata(libraryId, ColumnMetadata.named("library_id").withIndex(2).ofType(Types.OTHER).notNull());
        addMetadata(path, ColumnMetadata.named("path").withIndex(3).ofType(Types.VARCHAR).notNull());
        addMetadata(normalizedPath, ColumnMetadata.named("normalized_path").withIndex(4).ofType(Types.VARCHAR).notNull());
        addMetadata(createdAt, ColumnMetadata.named("created_at").withIndex(5).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
        addMetadata(updatedAt, ColumnMetadata.named("updated_at").withIndex(6).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "library_roots");
    }
}
