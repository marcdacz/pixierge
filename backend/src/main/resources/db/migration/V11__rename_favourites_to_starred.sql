-- Migrate any databases that applied the earlier favourites kind before it was renamed.
ALTER TABLE albums DROP CONSTRAINT IF EXISTS albums_kind_check;

UPDATE albums
SET kind = 'starred',
    name = CASE WHEN lower(name) = 'favourites' THEN 'Starred' ELSE name END
WHERE kind = 'favourites';

ALTER TABLE albums
    ADD CONSTRAINT albums_kind_check CHECK (kind IN ('user', 'starred'));

DROP INDEX IF EXISTS albums_one_favourites_per_owner_idx;

CREATE UNIQUE INDEX IF NOT EXISTS albums_one_starred_per_owner_idx
    ON albums (owner_user_id)
    WHERE kind = 'starred';
