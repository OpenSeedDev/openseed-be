ALTER TABLE point_ledgers DROP CONSTRAINT ck_point_ledgers_source_type;
ALTER TABLE point_ledgers ADD CONSTRAINT ck_point_ledgers_source_type CHECK (
    source_type IN (
        'SIGNUP_BONUS', 'DAILY_FIRST_ACCESS', 'IDEA_PUBLISHED',
        'FEEDBACK_CREATED', 'FEEDBACK_ACCEPTED', 'UNIT_PURCHASE', 'UNIT_RECOVERY',
        'PENDING_RECOVERY_PAYOUT'
    )
);

CREATE TABLE pending_recovery_payouts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    paid_amount INTEGER NOT NULL,
    balance_after INTEGER NOT NULL,
    policy_date DATE NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_pending_recovery_payouts_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_pending_recovery_payouts_paid_amount CHECK (paid_amount > 0),
    CONSTRAINT ck_pending_recovery_payouts_balance_after CHECK (balance_after BETWEEN 0 AND 2000)
);

CREATE INDEX idx_pending_recovery_payouts_user_paid_at
    ON pending_recovery_payouts (user_id, paid_at);
