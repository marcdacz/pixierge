CREATE TABLE album_members (
    album_id UUID NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    member_role TEXT NOT NULL CHECK (member_role IN ('viewer', 'editor')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (album_id, user_id)
);
CREATE INDEX album_members_user_id_idx ON album_members (user_id);

CREATE TABLE asset_library_state (
    asset_id UUID NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    library_id UUID NOT NULL REFERENCES libraries(id) ON DELETE CASCADE,
    privacy TEXT NOT NULL DEFAULT 'standard' CHECK (privacy IN ('standard', 'private')),
    updated_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (asset_id, library_id)
);

CREATE TABLE album_item_share_approvals (
    album_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    recipient_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_library_id UUID NOT NULL REFERENCES libraries(id) ON DELETE RESTRICT,
    approved_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    approved_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (album_id, asset_id, recipient_user_id),
    FOREIGN KEY (album_id, asset_id) REFERENCES album_items(album_id, asset_id) ON DELETE CASCADE
);
CREATE INDEX album_item_share_approvals_recipient_idx ON album_item_share_approvals (recipient_user_id, album_id);
