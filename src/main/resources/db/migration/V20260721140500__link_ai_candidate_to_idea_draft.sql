ALTER TABLE ideas
    ADD COLUMN source_ai_job_id UUID,
    ADD COLUMN source_ai_candidate_number INTEGER,
    ADD CONSTRAINT fk_ideas_source_ai_job
        FOREIGN KEY (source_ai_job_id) REFERENCES ai_jobs(id) ON DELETE CASCADE,
    ADD CONSTRAINT uk_ideas_source_ai_job UNIQUE (source_ai_job_id),
    ADD CONSTRAINT ck_ideas_source_ai_candidate
        CHECK (
            (source_ai_job_id IS NULL AND source_ai_candidate_number IS NULL)
            OR
            (source_ai_job_id IS NOT NULL
                AND source_ai_candidate_number IS NOT NULL
                AND source_ai_candidate_number BETWEEN 1 AND 5)
        );
