ALTER TABLE albums
    ADD COLUMN kind TEXT NOT NULL DEFAULT 'user';

ALTER TABLE albums
    ADD CONSTRAINT albums_kind_check CHECK (kind IN ('user', 'favourites'));

CREATE UNIQUE INDEX albums_one_favourites_per_owner_idx
    ON albums (owner_user_id)
    WHERE kind = 'favourites';
