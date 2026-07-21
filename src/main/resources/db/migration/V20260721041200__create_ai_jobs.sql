CREATE TABLE ai_jobs (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    input_snapshot JSONB NOT NULL,
    prompt_version VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ai_jobs_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT uk_ai_jobs_owner_idempotency_key UNIQUE (owner_id, idempotency_key),
    CONSTRAINT ck_ai_jobs_status CHECK (status IN ('PENDING')),
    CONSTRAINT ck_ai_jobs_input_snapshot_object CHECK (jsonb_typeof(input_snapshot) = 'object'),
    CONSTRAINT ck_ai_jobs_prompt_version_not_blank CHECK (btrim(prompt_version) <> ''),
    CONSTRAINT ck_ai_jobs_idempotency_key_not_blank CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT ck_ai_jobs_retry_count_non_negative CHECK (retry_count >= 0)
);

CREATE INDEX idx_ai_jobs_pending_created_at
    ON ai_jobs (status, created_at, id);
