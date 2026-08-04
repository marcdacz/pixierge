ALTER TABLE library_members DROP CONSTRAINT library_members_member_role_check;
ALTER TABLE library_members ADD CONSTRAINT library_members_member_role_check
    CHECK (member_role IN ('owner', 'admin', 'member'));
