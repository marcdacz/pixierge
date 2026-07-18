CREATE TABLE background_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_type TEXT NOT NULL,
    payload_json TEXT NOT NULL DEFAULT '{}',
    status TEXT NOT NULL CHECK (status IN ('pending', 'running', 'succeeded', 'failed', 'dead_letter', 'cancelled')),
    priority INTEGER NOT NULL DEFAULT 0,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    max_attempts INTEGER NOT NULL DEFAULT 3 CHECK (max_attempts > 0),
    next_run_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lease_until TIMESTAMPTZ NULL,
    locked_by TEXT NULL,
    concurrency_key TEXT NOT NULL,
    dedupe_key TEXT NULL,
    progress_json TEXT NULL,
    last_error_code TEXT NULL,
    last_error_message TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_background_jobs_ready
    ON background_jobs (status, next_run_at, priority DESC, created_at)
    WHERE status = 'pending';

CREATE INDEX idx_background_jobs_running_lease
    ON background_jobs (lease_until)
    WHERE status = 'running';

CREATE INDEX idx_background_jobs_type_status
    ON background_jobs (job_type, status);

CREATE INDEX idx_background_jobs_concurrency
    ON background_jobs (concurrency_key, status);

CREATE UNIQUE INDEX uq_background_jobs_active_dedupe
    ON background_jobs (dedupe_key)
    WHERE dedupe_key IS NOT NULL AND status IN ('pending', 'running');

CREATE UNIQUE INDEX uq_background_jobs_running_concurrency
    ON background_jobs (concurrency_key)
    WHERE status = 'running';
