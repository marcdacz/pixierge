ALTER TABLE asset_metadata
    ADD COLUMN metadata_status TEXT,
    ADD COLUMN metadata_extractor TEXT,
    ADD COLUMN metadata_extractor_version TEXT,
    ADD COLUMN metadata_schema_version INTEGER,
    ADD COLUMN metadata_source_file_size BIGINT,
    ADD COLUMN metadata_source_modified_at TIMESTAMPTZ,
    ADD COLUMN metadata_extracted_at TIMESTAMPTZ,
    ADD COLUMN metadata_error_code TEXT,
    ADD COLUMN metadata_error_message TEXT,
    ADD COLUMN lens_model TEXT,
    ADD COLUMN focal_length DOUBLE PRECISION,
    ADD COLUMN aperture DOUBLE PRECISION,
    ADD COLUMN exposure_time TEXT,
    ADD COLUMN iso INTEGER,
    ADD COLUMN latitude DOUBLE PRECISION,
    ADD COLUMN longitude DOUBLE PRECISION,
    ADD COLUMN title TEXT,
    ADD COLUMN description TEXT,
    ADD COLUMN keywords TEXT,
    ADD COLUMN duration_ms BIGINT,
    ADD COLUMN display_rotation INTEGER,
    ADD COLUMN container TEXT,
    ADD COLUMN video_codec TEXT,
    ADD COLUMN audio_codec TEXT,
    ADD COLUMN frame_rate TEXT,
    ADD COLUMN bitrate BIGINT,
    ADD COLUMN has_audio BOOLEAN;

UPDATE asset_metadata
SET metadata_status = extraction_status,
    metadata_extracted_at = extracted_at,
    metadata_error_message = error_message
WHERE metadata_status IS NULL;

ALTER TABLE asset_metadata
    ADD CONSTRAINT asset_metadata_status_check
        CHECK (metadata_status IN ('pending', 'processing', 'extracted', 'unsupported', 'failed', 'stale'));

ALTER TABLE asset_metadata
    DROP CONSTRAINT IF EXISTS asset_metadata_extraction_status_check,
    ADD CONSTRAINT asset_metadata_extraction_status_check
        CHECK (extraction_status IN ('pending', 'processing', 'extracted', 'unsupported', 'failed', 'stale'));

CREATE INDEX asset_metadata_metadata_status_idx ON asset_metadata (metadata_status);
CREATE INDEX asset_metadata_source_state_idx
    ON asset_metadata (metadata_source_file_size, metadata_source_modified_at, metadata_schema_version);
