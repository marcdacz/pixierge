CREATE TABLE thumbnails (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id UUID NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    content_hash TEXT NOT NULL,
    thumbnail_type TEXT NOT NULL CHECK (thumbnail_type IN ('tiny', 'grid', 'preview')),
    width INTEGER NOT NULL CHECK (width > 0),
    height INTEGER NOT NULL CHECK (height > 0),
    format TEXT NOT NULL CHECK (format IN ('jpg')),
    generator_version TEXT NOT NULL,
    config_version TEXT NOT NULL,
    cache_key TEXT NOT NULL,
    relative_path TEXT NOT NULL,
    byte_size BIGINT NOT NULL CHECK (byte_size >= 0),
    placeholder TEXT NULL,
    status TEXT NOT NULL CHECK (status IN ('ready', 'missing', 'stale', 'failed')),
    error_message TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    generated_at TIMESTAMPTZ NULL
);

CREATE UNIQUE INDEX uq_thumbnails_cache_inputs
    ON thumbnails (content_hash, thumbnail_type, width, height, format, generator_version, config_version);

CREATE INDEX idx_thumbnails_asset_id
    ON thumbnails (asset_id);
