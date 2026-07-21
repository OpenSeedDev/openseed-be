ALTER TABLE point_ledgers
    DROP CONSTRAINT ck_point_ledgers_source_type;

ALTER TABLE point_ledgers
    ADD CONSTRAINT ck_point_ledgers_source_type CHECK (
        source_type IN (
            'SIGNUP_BONUS',
            'DAILY_FIRST_ACCESS',
            'IDEA_PUBLISHED',
            'FEEDBACK_CREATED',
            'FEEDBACK_ACCEPTED'
        )
    );
