CREATE TABLE catalog_events (
    sequence BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_version INTEGER NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    actor_user_id UUID,
    payload_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    exported_at TIMESTAMPTZ
);

CREATE INDEX catalog_events_pending_idx ON catalog_events (sequence) WHERE exported_at IS NULL;

CREATE TABLE catalog_snapshots (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    through_sequence BIGINT NOT NULL,
    storage_path VARCHAR(1024) NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_detail VARCHAR(500)
);

CREATE INDEX catalog_snapshots_created_idx ON catalog_snapshots (created_at DESC);
