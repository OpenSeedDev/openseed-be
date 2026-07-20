CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    profile_id VARCHAR(20) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_profile_id CHECK (profile_id ~ '^[A-Za-z0-9_]{3,20}$'),
    CONSTRAINT ck_users_role CHECK (role IN ('USER')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE'))
);

CREATE TABLE point_wallets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    balance INTEGER NOT NULL,
    pending_recovery_balance INTEGER NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_point_wallets_user UNIQUE (user_id),
    CONSTRAINT fk_point_wallets_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_point_wallets_balance CHECK (balance BETWEEN 0 AND 2000),
    CONSTRAINT ck_point_wallets_pending CHECK (pending_recovery_balance >= 0)
);

CREATE TABLE point_ledgers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    type VARCHAR(30) NOT NULL,
    original_amount INTEGER NOT NULL,
    paid_amount INTEGER NOT NULL,
    expired_amount INTEGER NOT NULL,
    balance_after INTEGER NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_point_ledgers_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_point_ledgers_type CHECK (type IN ('CREDIT')),
    CONSTRAINT ck_point_ledgers_amounts CHECK (
        original_amount >= 0
        AND paid_amount >= 0
        AND expired_amount >= 0
        AND original_amount = paid_amount + expired_amount
    ),
    CONSTRAINT ck_point_ledgers_balance CHECK (balance_after BETWEEN 0 AND 2000),
    CONSTRAINT ck_point_ledgers_source_type CHECK (source_type IN ('SIGNUP_BONUS'))
);

CREATE INDEX idx_point_ledgers_user_created_at
    ON point_ledgers (user_id, created_at DESC);
