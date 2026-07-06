CREATE TABLE global_exclusion_patterns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pattern TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO global_exclusion_patterns (pattern)
VALUES
    ('**/@eaDir/**'),
    ('**/#recycle/**'),
    ('**/#snapshot/**'),
    ('**/.stversions/**'),
    ('**/.stfolder/**'),
    ('**/._*')
ON CONFLICT (pattern) DO NOTHING;

DELETE FROM library_exclusion_patterns
WHERE pattern IN (
    '**/@eaDir/**',
    '**/#recycle/**',
    '**/#snapshot/**',
    '**/.stversions/**',
    '**/.stfolder/**',
    '**/._*'
);
