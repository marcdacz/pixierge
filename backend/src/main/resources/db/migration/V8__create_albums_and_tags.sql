CREATE TABLE albums (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    cover_asset_id UUID REFERENCES assets(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX albums_owner_name_lower_idx ON albums (owner_user_id, lower(name));
CREATE INDEX albums_owner_user_id_idx ON albums (owner_user_id);
CREATE INDEX albums_cover_asset_id_idx ON albums (cover_asset_id);

CREATE TABLE album_items (
    album_id UUID NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
    asset_id UUID NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    source_library_id UUID NOT NULL REFERENCES libraries(id) ON DELETE RESTRICT,
    position INTEGER NOT NULL,
    added_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (album_id, asset_id)
);

CREATE INDEX album_items_asset_id_idx ON album_items (asset_id);
CREATE INDEX album_items_source_library_id_idx ON album_items (source_library_id);
CREATE INDEX album_items_album_position_idx ON album_items (album_id, position);

CREATE TABLE tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    normalized_name TEXT NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX tags_owner_normalized_name_idx ON tags (owner_user_id, normalized_name);
CREATE INDEX tags_owner_user_id_idx ON tags (owner_user_id);

CREATE TABLE asset_tags (
    tag_id UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    asset_id UUID NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    source_library_id UUID NOT NULL REFERENCES libraries(id) ON DELETE RESTRICT,
    added_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tag_id, asset_id)
);

CREATE INDEX asset_tags_asset_id_idx ON asset_tags (asset_id);
CREATE INDEX asset_tags_source_library_id_idx ON asset_tags (source_library_id);
