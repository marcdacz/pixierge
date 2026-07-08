package com.pixierge.api.db;

import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.ColumnMetadata;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SchemaAndTable;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;

public class QAssetMetadata extends RelationalPathBase<QAssetMetadata> {

    public static final QAssetMetadata assetMetadata = new QAssetMetadata("asset_metadata");

    public final ComparablePath<UUID> assetId = createComparable("assetId", UUID.class);
    public final DateTimePath<OffsetDateTime> capturedAt = createDateTime("capturedAt", OffsetDateTime.class);
    public final NumberPath<Integer> width = createNumber("width", Integer.class);
    public final NumberPath<Integer> height = createNumber("height", Integer.class);
    public final NumberPath<Integer> orientation = createNumber("orientation", Integer.class);
    public final StringPath fileExtension = createString("fileExtension");
    public final StringPath mimeType = createString("mimeType");
    public final StringPath cameraMake = createString("cameraMake");
    public final StringPath cameraModel = createString("cameraModel");
    public final StringPath sourceVersion = createString("sourceVersion");
    public final StringPath extractionStatus = createString("extractionStatus");
    public final DateTimePath<OffsetDateTime> extractedAt = createDateTime("extractedAt", OffsetDateTime.class);
    public final StringPath errorMessage = createString("errorMessage");

    public QAssetMetadata(String variable) {
        super(QAssetMetadata.class, forVariable(variable), null, "asset_metadata");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(assetId, ColumnMetadata.named("asset_id").withIndex(1).ofType(Types.OTHER).notNull());
        addMetadata(capturedAt, ColumnMetadata.named("captured_at").withIndex(2).ofType(Types.TIMESTAMP_WITH_TIMEZONE));
        addMetadata(width, ColumnMetadata.named("width").withIndex(3).ofType(Types.INTEGER));
        addMetadata(height, ColumnMetadata.named("height").withIndex(4).ofType(Types.INTEGER));
        addMetadata(orientation, ColumnMetadata.named("orientation").withIndex(5).ofType(Types.INTEGER));
        addMetadata(fileExtension, ColumnMetadata.named("file_extension").withIndex(6).ofType(Types.VARCHAR));
        addMetadata(mimeType, ColumnMetadata.named("mime_type").withIndex(7).ofType(Types.VARCHAR));
        addMetadata(cameraMake, ColumnMetadata.named("camera_make").withIndex(8).ofType(Types.VARCHAR));
        addMetadata(cameraModel, ColumnMetadata.named("camera_model").withIndex(9).ofType(Types.VARCHAR));
        addMetadata(sourceVersion, ColumnMetadata.named("source_version").withIndex(10).ofType(Types.VARCHAR).notNull());
        addMetadata(extractionStatus, ColumnMetadata.named("extraction_status").withIndex(11).ofType(Types.VARCHAR).notNull());
        addMetadata(extractedAt, ColumnMetadata.named("extracted_at").withIndex(12).ofType(Types.TIMESTAMP_WITH_TIMEZONE));
        addMetadata(errorMessage, ColumnMetadata.named("error_message").withIndex(13).ofType(Types.VARCHAR));
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "asset_metadata");
    }
}
