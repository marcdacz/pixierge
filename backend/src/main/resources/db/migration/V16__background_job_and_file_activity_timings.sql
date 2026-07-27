ALTER TABLE background_jobs
    ADD COLUMN started_at TIMESTAMPTZ;

ALTER TABLE file_observations
    ADD COLUMN processing_duration_ms BIGINT;

ALTER TABLE asset_metadata
    ADD COLUMN metadata_extraction_duration_ms BIGINT;
