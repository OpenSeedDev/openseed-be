ALTER TABLE ai_jobs
    ADD COLUMN lease_owner VARCHAR(100),
    ADD COLUMN lease_token UUID,
    ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE ai_jobs DROP CONSTRAINT ck_ai_jobs_status;
ALTER TABLE ai_jobs ADD CONSTRAINT ck_ai_jobs_status
    CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT'));

ALTER TABLE ai_jobs ADD CONSTRAINT ck_ai_jobs_worker_state CHECK (
    (status = 'PENDING'
        AND lease_owner IS NULL AND lease_token IS NULL
        AND locked_until IS NULL AND next_attempt_at IS NULL)
    OR
    (status = 'PROCESSING'
        AND lease_owner IS NOT NULL AND btrim(lease_owner) <> ''
        AND lease_token IS NOT NULL AND locked_until IS NOT NULL
        AND next_attempt_at IS NULL)
    OR
    (status = 'RETRY_WAIT'
        AND lease_owner IS NULL AND lease_token IS NULL
        AND locked_until IS NULL AND next_attempt_at IS NOT NULL)
);

CREATE INDEX idx_ai_jobs_claimable
    ON ai_jobs (status, next_attempt_at, locked_until, created_at, id);
