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

public class QTags extends RelationalPathBase<QTags> {

    public static final QTags tags = new QTags("tags");

    public final ComparablePath<UUID> id = createComparable("id", UUID.class);
    public final ComparablePath<UUID> ownerUserId = createComparable("ownerUserId", UUID.class);
    public final StringPath name = createString("name");
    public final StringPath normalizedName = createString("normalizedName");
    public final ComparablePath<UUID> createdBy = createComparable("createdBy", UUID.class);
    public final DateTimePath<OffsetDateTime> createdAt = createDateTime("createdAt", OffsetDateTime.class);
    public final DateTimePath<OffsetDateTime> updatedAt = createDateTime("updatedAt", OffsetDateTime.class);

    public QTags(String variable) {
        super(QTags.class, forVariable(variable), null, "tags");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(id, ColumnMetadata.named("id").withIndex(1).ofType(Types.OTHER).notNull());
        addMetadata(ownerUserId, ColumnMetadata.named("owner_user_id").withIndex(2).ofType(Types.OTHER).notNull());
        addMetadata(name, ColumnMetadata.named("name").withIndex(3).ofType(Types.VARCHAR).notNull());
        addMetadata(normalizedName, ColumnMetadata.named("normalized_name").withIndex(4).ofType(Types.VARCHAR).notNull());
        addMetadata(createdBy, ColumnMetadata.named("created_by").withIndex(5).ofType(Types.OTHER).notNull());
        addMetadata(createdAt, ColumnMetadata.named("created_at").withIndex(6).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
        addMetadata(updatedAt, ColumnMetadata.named("updated_at").withIndex(7).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "tags");
    }
}
