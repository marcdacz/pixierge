CREATE TABLE app_metadata (
    metadata_key TEXT PRIMARY KEY,
    metadata_value TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO app_metadata (metadata_key, metadata_value)
VALUES ('schema_marker', 'baseline');
