CREATE TABLE file_activity_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id UUID REFERENCES assets(id) ON DELETE SET NULL,
    path TEXT NOT NULL,
    status TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    message TEXT,
    duration_ms BIGINT,
    job_id UUID REFERENCES background_jobs(id) ON DELETE SET NULL,
    batch_label TEXT
);

CREATE INDEX file_activity_events_occurred_at_idx ON file_activity_events (occurred_at DESC);
CREATE INDEX file_activity_events_asset_id_idx ON file_activity_events (asset_id);

DELETE FROM file_observations;
