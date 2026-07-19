ALTER TABLE file_observations
    ADD COLUMN observed_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX file_observations_observed_at_idx ON file_observations (observed_at DESC);
