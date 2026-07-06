ALTER TABLE libraries
    ADD COLUMN status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'archived')),
    ADD COLUMN archived_at TIMESTAMPTZ;

CREATE INDEX libraries_status_name_idx ON libraries (status, lower(name));

CREATE TABLE library_exclusion_patterns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    library_id UUID NOT NULL REFERENCES libraries(id) ON DELETE CASCADE,
    pattern TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (library_id, pattern)
);

CREATE INDEX library_exclusion_patterns_library_id_idx ON library_exclusion_patterns (library_id);

CREATE TABLE assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_hash TEXT NOT NULL UNIQUE,
    media_type TEXT NOT NULL,
    available_file_count INTEGER NOT NULL DEFAULT 0,
    first_observed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_observed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE scan_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    library_id UUID NOT NULL REFERENCES libraries(id) ON DELETE CASCADE,
    root_id UUID REFERENCES library_roots(id) ON DELETE SET NULL,
    requested_by UUID REFERENCES users(id) ON DELETE SET NULL,
    status TEXT NOT NULL CHECK (status IN ('queued', 'running', 'completed', 'completed_with_errors', 'failed', 'cancelled')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    scanned_file_count INTEGER NOT NULL DEFAULT 0,
    added_count INTEGER NOT NULL DEFAULT 0,
    unchanged_count INTEGER NOT NULL DEFAULT 0,
    moved_count INTEGER NOT NULL DEFAULT 0,
    modified_count INTEGER NOT NULL DEFAULT 0,
    duplicate_count INTEGER NOT NULL DEFAULT 0,
    missing_count INTEGER NOT NULL DEFAULT 0,
    reappeared_count INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX scan_runs_library_id_started_at_idx ON scan_runs (library_id, started_at DESC);
CREATE INDEX scan_runs_root_id_idx ON scan_runs (root_id);

CREATE TABLE asset_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id UUID NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    library_id UUID NOT NULL REFERENCES libraries(id) ON DELETE CASCADE,
    root_id UUID NOT NULL REFERENCES library_roots(id) ON DELETE CASCADE,
    path TEXT NOT NULL,
    normalized_path TEXT NOT NULL,
    file_name TEXT NOT NULL,
    size_bytes BIGINT NOT NULL,
    modified_at TIMESTAMPTZ NOT NULL,
    content_hash TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('active', 'missing', 'superseded')),
    first_observed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_observed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_scan_run_id UUID REFERENCES scan_runs(id) ON DELETE SET NULL,
    replaced_by_file_id UUID REFERENCES asset_files(id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX asset_files_active_path_idx ON asset_files (library_id, normalized_path) WHERE status = 'active';
CREATE INDEX asset_files_asset_id_idx ON asset_files (asset_id);
CREATE INDEX asset_files_library_id_idx ON asset_files (library_id);
CREATE INDEX asset_files_last_seen_scan_run_id_idx ON asset_files (last_seen_scan_run_id);
CREATE INDEX asset_files_content_hash_idx ON asset_files (content_hash);

CREATE TABLE file_observations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scan_run_id UUID NOT NULL REFERENCES scan_runs(id) ON DELETE CASCADE,
    library_id UUID NOT NULL REFERENCES libraries(id) ON DELETE CASCADE,
    root_id UUID NOT NULL REFERENCES library_roots(id) ON DELETE CASCADE,
    asset_id UUID REFERENCES assets(id) ON DELETE SET NULL,
    asset_file_id UUID REFERENCES asset_files(id) ON DELETE SET NULL,
    path TEXT NOT NULL,
    normalized_path TEXT NOT NULL,
    size_bytes BIGINT,
    modified_at TIMESTAMPTZ,
    partial_hash TEXT,
    content_hash TEXT,
    result TEXT NOT NULL CHECK (result IN ('added', 'unchanged', 'moved', 'renamed', 'modified', 'duplicate', 'missing', 'reappeared', 'error'))
);

CREATE INDEX file_observations_scan_run_id_idx ON file_observations (scan_run_id);
CREATE INDEX file_observations_library_id_idx ON file_observations (library_id);
CREATE INDEX file_observations_content_hash_idx ON file_observations (content_hash);

CREATE TABLE scan_errors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scan_run_id UUID NOT NULL REFERENCES scan_runs(id) ON DELETE CASCADE,
    library_id UUID NOT NULL REFERENCES libraries(id) ON DELETE CASCADE,
    root_id UUID REFERENCES library_roots(id) ON DELETE SET NULL,
    path TEXT,
    error_code TEXT NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX scan_errors_scan_run_id_idx ON scan_errors (scan_run_id);
