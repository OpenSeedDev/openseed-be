CREATE TABLE feedbacks (
    id UUID PRIMARY KEY,
    idea_id UUID NOT NULL,
    user_id UUID NOT NULL,
    feedback_type VARCHAR(30) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    evidence_url VARCHAR(2048),
    evidence_description VARCHAR(1000),
    accepted_at TIMESTAMP WITH TIME ZONE,
    edited_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_feedbacks_idea FOREIGN KEY (idea_id) REFERENCES ideas (id) ON DELETE CASCADE,
    CONSTRAINT fk_feedbacks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_feedbacks_type CHECK (
        feedback_type IN (
            'PROBLEM_EMPATHY', 'TARGET_CUSTOMER', 'SOLUTION',
            'BUSINESS_MODEL', 'COMPETITION', 'OTHER'
        )
    ),
    CONSTRAINT ck_feedbacks_content_length CHECK (char_length(content) BETWEEN 100 AND 2000),
    CONSTRAINT ck_feedbacks_evidence_url CHECK (
        evidence_url IS NULL OR evidence_url ~* '^https?://[^[:space:]]+$'
    )
);

CREATE INDEX idx_feedbacks_idea_created_at
    ON feedbacks (idea_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_feedbacks_user_created_at
    ON feedbacks (user_id, created_at DESC, id DESC);
