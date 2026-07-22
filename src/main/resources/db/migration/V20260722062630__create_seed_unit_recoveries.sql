ALTER TABLE point_ledgers DROP CONSTRAINT ck_point_ledgers_source_type;
ALTER TABLE point_ledgers ADD CONSTRAINT ck_point_ledgers_source_type CHECK (
    source_type IN (
        'SIGNUP_BONUS', 'DAILY_FIRST_ACCESS', 'IDEA_PUBLISHED',
        'FEEDBACK_CREATED', 'FEEDBACK_ACCEPTED', 'UNIT_PURCHASE', 'UNIT_RECOVERY'
    )
);

CREATE TABLE seed_unit_recoveries (
    id UUID PRIMARY KEY,
    lot_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    idea_id UUID NOT NULL,
    units INTEGER NOT NULL,
    recovery_price INTEGER NOT NULL,
    realized_amount INTEGER NOT NULL,
    wallet_paid_amount INTEGER NOT NULL,
    pending_amount INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_seed_unit_recoveries_lot FOREIGN KEY (lot_id) REFERENCES seed_unit_lots (id) ON DELETE CASCADE,
    CONSTRAINT fk_seed_unit_recoveries_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_seed_unit_recoveries_idea FOREIGN KEY (idea_id) REFERENCES ideas (id) ON DELETE CASCADE,
    CONSTRAINT ck_seed_unit_recoveries_units CHECK (units > 0),
    CONSTRAINT ck_seed_unit_recoveries_price CHECK (recovery_price BETWEEN 1 AND 100),
    CONSTRAINT ck_seed_unit_recoveries_amounts CHECK (
        realized_amount = units * recovery_price
        AND wallet_paid_amount >= 0
        AND pending_amount >= 0
        AND realized_amount = wallet_paid_amount + pending_amount
    )
);

CREATE INDEX idx_seed_unit_recoveries_user_created_at
    ON seed_unit_recoveries (user_id, created_at);
