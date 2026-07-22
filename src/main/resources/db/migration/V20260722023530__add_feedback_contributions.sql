CREATE TABLE contributions (
    id UUID PRIMARY KEY,
    idea_id UUID NOT NULL,
    user_id UUID NOT NULL,
    source_feedback_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_contributions_idea FOREIGN KEY (idea_id) REFERENCES ideas (id) ON DELETE CASCADE,
    CONSTRAINT fk_contributions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_contributions_feedback FOREIGN KEY (source_feedback_id) REFERENCES feedbacks (id) ON DELETE CASCADE
);

CREATE INDEX idx_contributions_user_created_at
    ON contributions (user_id, created_at DESC, id DESC);
CREATE INDEX idx_contributions_idea_created_at
    ON contributions (idea_id, created_at DESC, id DESC);

ALTER TABLE point_ledgers ADD COLUMN reward_scope_id UUID;
CREATE INDEX idx_point_ledgers_scoped_reward
    ON point_ledgers (user_id, source_type, reward_scope_id, policy_date)
    WHERE reward_scope_id IS NOT NULL;

ALTER TABLE idea_timeline_events ADD COLUMN source_id UUID;
ALTER TABLE idea_timeline_events DROP CONSTRAINT ck_idea_timeline_event_type;
ALTER TABLE idea_timeline_events ADD CONSTRAINT ck_idea_timeline_event_type
    CHECK (event_type IN ('PUBLISHED', 'UPDATED', 'FEEDBACK_ACCEPTED'));
CREATE UNIQUE INDEX uk_timeline_feedback_accepted_source
    ON idea_timeline_events (source_id)
    WHERE event_type = 'FEEDBACK_ACCEPTED';
