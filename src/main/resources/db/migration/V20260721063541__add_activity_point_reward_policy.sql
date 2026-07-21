ALTER TABLE point_ledgers
    ADD COLUMN policy_date DATE;

ALTER TABLE point_ledgers
    DROP CONSTRAINT ck_point_ledgers_source_type;

ALTER TABLE point_ledgers
    ADD CONSTRAINT ck_point_ledgers_source_type CHECK (
        source_type IN ('SIGNUP_BONUS', 'IDEA_PUBLISHED', 'FEEDBACK_CREATED', 'FEEDBACK_ACCEPTED')
    );

ALTER TABLE point_ledgers
    ADD CONSTRAINT ck_point_ledgers_activity_policy_date CHECK (
        (source_type = 'SIGNUP_BONUS' AND policy_date IS NULL)
        OR (source_type <> 'SIGNUP_BONUS' AND policy_date IS NOT NULL)
    );

ALTER TABLE point_ledgers
    ADD CONSTRAINT uk_point_ledgers_reward_source UNIQUE (source_type, source_id);

CREATE INDEX idx_point_ledgers_user_policy_date
    ON point_ledgers (user_id, policy_date)
    WHERE policy_date IS NOT NULL;
