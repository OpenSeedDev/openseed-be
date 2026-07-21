ALTER TABLE ai_jobs
    ADD COLUMN failure_code VARCHAR(50);

ALTER TABLE ai_jobs DROP CONSTRAINT ck_ai_jobs_status;
ALTER TABLE ai_jobs ADD CONSTRAINT ck_ai_jobs_status
    CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT', 'SUCCEEDED', 'FAILED'));

ALTER TABLE ai_jobs DROP CONSTRAINT ck_ai_jobs_worker_state;
ALTER TABLE ai_jobs ADD CONSTRAINT ck_ai_jobs_worker_state CHECK (
    (status = 'PENDING'
        AND lease_owner IS NULL AND lease_token IS NULL
        AND locked_until IS NULL AND next_attempt_at IS NULL AND failure_code IS NULL)
    OR
    (status = 'PROCESSING'
        AND lease_owner IS NOT NULL AND btrim(lease_owner) <> ''
        AND lease_token IS NOT NULL AND locked_until IS NOT NULL
        AND next_attempt_at IS NULL AND failure_code IS NULL)
    OR
    (status = 'RETRY_WAIT'
        AND lease_owner IS NULL AND lease_token IS NULL
        AND locked_until IS NULL AND next_attempt_at IS NOT NULL AND failure_code IS NULL)
    OR
    (status = 'SUCCEEDED'
        AND lease_owner IS NULL AND lease_token IS NULL
        AND locked_until IS NULL AND next_attempt_at IS NULL AND failure_code IS NULL)
    OR
    (status = 'FAILED'
        AND lease_owner IS NULL AND lease_token IS NULL
        AND locked_until IS NULL AND next_attempt_at IS NULL
        AND failure_code IS NOT NULL AND btrim(failure_code) <> '')
);

CREATE TABLE ai_generation_results (
    id UUID PRIMARY KEY,
    ai_job_id UUID NOT NULL,
    raw_result JSONB NOT NULL,
    normalized_result JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ai_generation_results_job FOREIGN KEY (ai_job_id) REFERENCES ai_jobs (id),
    CONSTRAINT uk_ai_generation_results_job UNIQUE (ai_job_id),
    CONSTRAINT ck_ai_generation_results_raw_object CHECK (jsonb_typeof(raw_result) = 'object'),
    CONSTRAINT ck_ai_generation_results_normalized_object CHECK (jsonb_typeof(normalized_result) = 'object')
);
