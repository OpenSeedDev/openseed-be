ALTER TABLE point_ledgers DROP CONSTRAINT ck_point_ledgers_type;
ALTER TABLE point_ledgers ADD CONSTRAINT ck_point_ledgers_type
    CHECK (type IN ('CREDIT', 'DEBIT'));

ALTER TABLE point_ledgers DROP CONSTRAINT ck_point_ledgers_source_type;
ALTER TABLE point_ledgers ADD CONSTRAINT ck_point_ledgers_source_type CHECK (
    source_type IN (
        'SIGNUP_BONUS', 'DAILY_FIRST_ACCESS', 'IDEA_PUBLISHED',
        'FEEDBACK_CREATED', 'FEEDBACK_ACCEPTED', 'UNIT_PURCHASE'
    )
);

CREATE TABLE seed_unit_lots (
    id UUID PRIMARY KEY,
    idea_id UUID NOT NULL,
    user_id UUID NOT NULL,
    units INTEGER NOT NULL,
    purchase_price INTEGER NOT NULL,
    principal INTEGER NOT NULL,
    purchased_at TIMESTAMP WITH TIME ZONE NOT NULL,
    unlocked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT fk_seed_unit_lots_idea FOREIGN KEY (idea_id) REFERENCES ideas (id) ON DELETE CASCADE,
    CONSTRAINT fk_seed_unit_lots_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_seed_unit_lots_units CHECK (units > 0),
    CONSTRAINT ck_seed_unit_lots_price CHECK (purchase_price BETWEEN 1 AND 100),
    CONSTRAINT ck_seed_unit_lots_principal CHECK (principal = units * purchase_price AND principal > 0),
    CONSTRAINT ck_seed_unit_lots_lock CHECK (unlocked_at = purchased_at + INTERVAL '24 hours'),
    CONSTRAINT ck_seed_unit_lots_status CHECK (status IN ('LOCKED', 'RECOVERED'))
);

CREATE INDEX idx_seed_unit_lots_user_idea_status
    ON seed_unit_lots (user_id, idea_id, status);

CREATE INDEX idx_point_ledgers_unit_purchase_daily
    ON point_ledgers (user_id, policy_date)
    WHERE source_type = 'UNIT_PURCHASE';
