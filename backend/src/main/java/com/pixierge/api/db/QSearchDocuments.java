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

public class QSearchDocuments extends RelationalPathBase<QSearchDocuments> {

    public static final QSearchDocuments searchDocuments = new QSearchDocuments("search_documents");

    public final ComparablePath<UUID> assetId = createComparable("assetId", UUID.class);
    public final StringPath searchableText = createString("searchableText");
    public final DateTimePath<OffsetDateTime> updatedAt = createDateTime("updatedAt", OffsetDateTime.class);

    public QSearchDocuments(String variable) {
        super(QSearchDocuments.class, forVariable(variable), null, "search_documents");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(assetId, ColumnMetadata.named("asset_id").withIndex(1).ofType(Types.OTHER).notNull());
        addMetadata(searchableText, ColumnMetadata.named("searchable_text").withIndex(2).ofType(Types.VARCHAR).notNull());
        addMetadata(updatedAt, ColumnMetadata.named("updated_at").withIndex(4).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "search_documents");
    }
}
