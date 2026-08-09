ALTER TABLE catalog_events RENAME TO audit_events;
DROP INDEX catalog_events_pending_idx;
ALTER TABLE audit_events DROP COLUMN exported_at;
ALTER TABLE audit_events RENAME CONSTRAINT catalog_events_pkey TO audit_events_pkey;
ALTER TABLE audit_events RENAME CONSTRAINT catalog_events_event_id_key TO audit_events_event_id_key;
CREATE INDEX audit_events_created_idx ON audit_events (created_at DESC);

CREATE TABLE database_backups (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    storage_path VARCHAR(1024) NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    postgres_version VARCHAR(40) NOT NULL,
    schema_version VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_detail VARCHAR(500)
);

CREATE INDEX database_backups_created_idx ON database_backups (created_at DESC);
