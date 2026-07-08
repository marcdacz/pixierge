CREATE TABLE asset_metadata (
    asset_id UUID PRIMARY KEY REFERENCES assets(id) ON DELETE CASCADE,
    captured_at TIMESTAMPTZ,
    width INTEGER,
    height INTEGER,
    orientation INTEGER,
    file_extension TEXT,
    mime_type TEXT,
    camera_make TEXT,
    camera_model TEXT,
    source_version TEXT NOT NULL,
    extraction_status TEXT NOT NULL CHECK (extraction_status IN ('pending', 'extracted', 'unsupported', 'failed')),
    extracted_at TIMESTAMPTZ,
    error_message TEXT
);

CREATE INDEX asset_metadata_captured_at_idx ON asset_metadata (captured_at);
CREATE INDEX asset_metadata_extraction_status_idx ON asset_metadata (extraction_status);

CREATE TABLE search_documents (
    asset_id UUID PRIMARY KEY REFERENCES assets(id) ON DELETE CASCADE,
    searchable_text TEXT NOT NULL,
    search_vector TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', searchable_text)) STORED,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX search_documents_vector_idx ON search_documents USING GIN (search_vector);
