CREATE TABLE scheduled_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_key TEXT NOT NULL,
    display_name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    owner_type TEXT NOT NULL CHECK (owner_type IN ('core', 'plugin')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    cron_expression TEXT NOT NULL,
    timezone TEXT NOT NULL DEFAULT 'UTC',
    next_run_at TIMESTAMPTZ NULL,
    last_run_at TIMESTAMPTZ NULL,
    last_status TEXT NULL CHECK (last_status IS NULL OR last_status IN ('queued', 'running', 'succeeded', 'failed', 'skipped')),
    timeout_seconds INTEGER NOT NULL DEFAULT 3600 CHECK (timeout_seconds > 0),
    concurrency_key TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_scheduled_jobs_job_key UNIQUE (job_key)
);

CREATE INDEX idx_scheduled_jobs_due
    ON scheduled_jobs (enabled, next_run_at)
    WHERE enabled = TRUE;

CREATE TABLE scheduled_job_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES scheduled_jobs(id) ON DELETE CASCADE,
    trigger_source TEXT NOT NULL CHECK (trigger_source IN ('scheduled', 'manual')),
    status TEXT NOT NULL CHECK (status IN ('queued', 'running', 'succeeded', 'failed', 'skipped')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ NULL,
    duration_ms BIGINT NULL CHECK (duration_ms IS NULL OR duration_ms >= 0),
    summary_json TEXT NULL,
    error_message TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_scheduled_job_runs_job_started
    ON scheduled_job_runs (job_id, started_at DESC);

CREATE TABLE scheduled_job_locks (
    concurrency_key TEXT PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES scheduled_jobs(id) ON DELETE CASCADE,
    run_id UUID NOT NULL REFERENCES scheduled_job_runs(id) ON DELETE CASCADE,
    acquired_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
