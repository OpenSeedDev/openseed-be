CREATE TABLE company_verifications (
    id UUID PRIMARY KEY,
    company_profile_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    invalidated_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_company_verifications_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_company_verifications_profile FOREIGN KEY (company_profile_id)
        REFERENCES company_profiles (id) ON DELETE CASCADE,
    CONSTRAINT ck_company_verifications_hash_lower_hex
        CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_company_verifications_expiry
        CHECK (expires_at > created_at),
    CONSTRAINT ck_company_verifications_terminal_state
        CHECK (used_at IS NULL OR invalidated_at IS NULL)
);

CREATE UNIQUE INDEX uk_company_verifications_active_profile
    ON company_verifications (company_profile_id)
    WHERE used_at IS NULL AND invalidated_at IS NULL;

CREATE INDEX idx_company_verifications_profile_created
    ON company_verifications (company_profile_id, created_at DESC);
