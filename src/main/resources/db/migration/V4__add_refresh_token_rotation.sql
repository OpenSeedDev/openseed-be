ALTER TABLE auth_sessions ADD COLUMN family_id UUID;
ALTER TABLE auth_sessions ADD COLUMN rotated_from_id UUID NULL;
ALTER TABLE auth_sessions ADD COLUMN revocation_reason VARCHAR(30) NULL;

UPDATE auth_sessions SET family_id = id;
ALTER TABLE auth_sessions ALTER COLUMN family_id SET NOT NULL;

ALTER TABLE auth_sessions ADD CONSTRAINT fk_auth_sessions_rotated_from
    FOREIGN KEY (rotated_from_id) REFERENCES auth_sessions(id);
ALTER TABLE auth_sessions ADD CONSTRAINT uk_auth_sessions_rotated_from UNIQUE (rotated_from_id);
ALTER TABLE auth_sessions ADD CONSTRAINT ck_auth_sessions_revocation_reason
    CHECK (revocation_reason IS NULL OR revocation_reason IN ('ROTATED', 'REUSE_DETECTED', 'USER_SUSPENDED'));

CREATE INDEX idx_auth_sessions_family_active ON auth_sessions(family_id, revoked_at);
