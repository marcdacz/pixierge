ALTER TABLE albums
    ADD COLUMN kind TEXT NOT NULL DEFAULT 'user';

ALTER TABLE albums
    ADD CONSTRAINT albums_kind_check CHECK (kind IN ('user', 'starred'));

CREATE UNIQUE INDEX albums_one_starred_per_owner_idx
    ON albums (owner_user_id)
    WHERE kind = 'starred';
