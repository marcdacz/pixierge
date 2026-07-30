INSERT INTO permissions (permission_key, description)
VALUES
    ('album:write', 'Create and manage owned albums'),
    ('tag:write', 'Create and manage owned tags'),
    ('sharing:write', 'Manage library and album sharing')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.permission_key IN ('album:write', 'tag:write', 'sharing:write')
WHERE r.role_key IN ('ADMIN', 'USER')
ON CONFLICT DO NOTHING;
