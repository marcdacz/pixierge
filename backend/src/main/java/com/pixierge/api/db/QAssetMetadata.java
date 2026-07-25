package com.pixierge.api.db;

import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.BooleanPath;
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
    public final StringPath metadataStatus = createString("metadataStatus");
    public final StringPath metadataExtractor = createString("metadataExtractor");
    public final StringPath metadataExtractorVersion = createString("metadataExtractorVersion");
    public final NumberPath<Integer> metadataSchemaVersion = createNumber("metadataSchemaVersion", Integer.class);
    public final NumberPath<Long> metadataSourceFileSize = createNumber("metadataSourceFileSize", Long.class);
    public final DateTimePath<OffsetDateTime> metadataSourceModifiedAt = createDateTime("metadataSourceModifiedAt", OffsetDateTime.class);
    public final DateTimePath<OffsetDateTime> metadataExtractedAt = createDateTime("metadataExtractedAt", OffsetDateTime.class);
    public final StringPath metadataErrorCode = createString("metadataErrorCode");
    public final StringPath metadataErrorMessage = createString("metadataErrorMessage");
    public final StringPath lensModel = createString("lensModel");
    public final NumberPath<Double> focalLength = createNumber("focalLength", Double.class);
    public final NumberPath<Double> aperture = createNumber("aperture", Double.class);
    public final StringPath exposureTime = createString("exposureTime");
    public final NumberPath<Integer> iso = createNumber("iso", Integer.class);
    public final NumberPath<Double> latitude = createNumber("latitude", Double.class);
    public final NumberPath<Double> longitude = createNumber("longitude", Double.class);
    public final StringPath title = createString("title");
    public final StringPath description = createString("description");
    public final StringPath keywords = createString("keywords");
    public final NumberPath<Long> durationMs = createNumber("durationMs", Long.class);
    public final NumberPath<Integer> displayRotation = createNumber("displayRotation", Integer.class);
    public final StringPath container = createString("container");
    public final StringPath videoCodec = createString("videoCodec");
    public final StringPath audioCodec = createString("audioCodec");
    public final StringPath frameRate = createString("frameRate");
    public final NumberPath<Long> bitrate = createNumber("bitrate", Long.class);
    public final BooleanPath hasAudio = createBoolean("hasAudio");

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
        addMetadata(metadataStatus, ColumnMetadata.named("metadata_status").withIndex(14).ofType(Types.VARCHAR));
        addMetadata(metadataExtractor, ColumnMetadata.named("metadata_extractor").withIndex(15).ofType(Types.VARCHAR));
        addMetadata(metadataExtractorVersion, ColumnMetadata.named("metadata_extractor_version").withIndex(16).ofType(Types.VARCHAR));
        addMetadata(metadataSchemaVersion, ColumnMetadata.named("metadata_schema_version").withIndex(17).ofType(Types.INTEGER));
        addMetadata(metadataSourceFileSize, ColumnMetadata.named("metadata_source_file_size").withIndex(18).ofType(Types.BIGINT));
        addMetadata(metadataSourceModifiedAt, ColumnMetadata.named("metadata_source_modified_at").withIndex(19).ofType(Types.TIMESTAMP_WITH_TIMEZONE));
        addMetadata(metadataExtractedAt, ColumnMetadata.named("metadata_extracted_at").withIndex(20).ofType(Types.TIMESTAMP_WITH_TIMEZONE));
        addMetadata(metadataErrorCode, ColumnMetadata.named("metadata_error_code").withIndex(21).ofType(Types.VARCHAR));
        addMetadata(metadataErrorMessage, ColumnMetadata.named("metadata_error_message").withIndex(22).ofType(Types.VARCHAR));
        addMetadata(lensModel, ColumnMetadata.named("lens_model").withIndex(23).ofType(Types.VARCHAR));
        addMetadata(focalLength, ColumnMetadata.named("focal_length").withIndex(24).ofType(Types.DOUBLE));
        addMetadata(aperture, ColumnMetadata.named("aperture").withIndex(25).ofType(Types.DOUBLE));
        addMetadata(exposureTime, ColumnMetadata.named("exposure_time").withIndex(26).ofType(Types.VARCHAR));
        addMetadata(iso, ColumnMetadata.named("iso").withIndex(27).ofType(Types.INTEGER));
        addMetadata(latitude, ColumnMetadata.named("latitude").withIndex(28).ofType(Types.DOUBLE));
        addMetadata(longitude, ColumnMetadata.named("longitude").withIndex(29).ofType(Types.DOUBLE));
        addMetadata(title, ColumnMetadata.named("title").withIndex(30).ofType(Types.VARCHAR));
        addMetadata(description, ColumnMetadata.named("description").withIndex(31).ofType(Types.VARCHAR));
        addMetadata(keywords, ColumnMetadata.named("keywords").withIndex(32).ofType(Types.VARCHAR));
        addMetadata(durationMs, ColumnMetadata.named("duration_ms").withIndex(33).ofType(Types.BIGINT));
        addMetadata(displayRotation, ColumnMetadata.named("display_rotation").withIndex(34).ofType(Types.INTEGER));
        addMetadata(container, ColumnMetadata.named("container").withIndex(35).ofType(Types.VARCHAR));
        addMetadata(videoCodec, ColumnMetadata.named("video_codec").withIndex(36).ofType(Types.VARCHAR));
        addMetadata(audioCodec, ColumnMetadata.named("audio_codec").withIndex(37).ofType(Types.VARCHAR));
        addMetadata(frameRate, ColumnMetadata.named("frame_rate").withIndex(38).ofType(Types.VARCHAR));
        addMetadata(bitrate, ColumnMetadata.named("bitrate").withIndex(39).ofType(Types.BIGINT));
        addMetadata(hasAudio, ColumnMetadata.named("has_audio").withIndex(40).ofType(Types.BOOLEAN));
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "asset_metadata");
    }
}
