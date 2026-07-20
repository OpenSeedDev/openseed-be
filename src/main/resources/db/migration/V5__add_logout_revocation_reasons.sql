ALTER TABLE auth_sessions DROP CONSTRAINT ck_auth_sessions_revocation_reason;
ALTER TABLE auth_sessions ADD CONSTRAINT ck_auth_sessions_revocation_reason
    CHECK (revocation_reason IS NULL OR revocation_reason IN (
        'ROTATED', 'REUSE_DETECTED', 'USER_SUSPENDED', 'LOGOUT', 'LOGOUT_ALL'
    ));
