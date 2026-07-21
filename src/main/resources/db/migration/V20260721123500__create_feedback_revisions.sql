CREATE TABLE feedback_revisions (
    id UUID PRIMARY KEY,
    feedback_id UUID NOT NULL,
    revision_type VARCHAR(20) NOT NULL,
    feedback_type VARCHAR(30) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    evidence_url VARCHAR(2048),
    evidence_description VARCHAR(1000),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_feedback_revisions_feedback
        FOREIGN KEY (feedback_id) REFERENCES feedbacks (id) ON DELETE CASCADE,
    CONSTRAINT ck_feedback_revisions_revision_type
        CHECK (revision_type IN ('EDITED', 'DELETED')),
    CONSTRAINT ck_feedback_revisions_feedback_type
        CHECK (feedback_type IN (
            'PROBLEM_EMPATHY', 'TARGET_CUSTOMER', 'SOLUTION',
            'BUSINESS_MODEL', 'COMPETITION', 'OTHER'
        )),
    CONSTRAINT ck_feedback_revisions_content_length
        CHECK (char_length(content) BETWEEN 100 AND 2000)
);

CREATE INDEX idx_feedback_revisions_feedback_recorded_at
    ON feedback_revisions (feedback_id, recorded_at DESC, id DESC);
