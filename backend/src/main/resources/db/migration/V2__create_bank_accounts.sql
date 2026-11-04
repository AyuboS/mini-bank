-- Bank accounts. SYSTEM accounts represent the bank's own funding position: they are the
-- counterparty for deposits and withdrawals, which is what keeps the ledger double-entry balanced.
-- Only SYSTEM accounts are permitted to hold a negative balance.
CREATE TABLE bank_accounts (
    id             UUID           PRIMARY KEY,
    account_number VARCHAR(20)    NOT NULL,
    user_id        UUID,
    account_type   VARCHAR(20)    NOT NULL,
    currency       VARCHAR(3)     NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    balance        NUMERIC(19, 4) NOT NULL DEFAULT 0,
    version        BIGINT         NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,

    CONSTRAINT uq_bank_accounts_number UNIQUE (account_number),
    CONSTRAINT fk_bank_accounts_user   FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_bank_accounts_type   CHECK (account_type IN ('CUSTOMER', 'SYSTEM')),
    CONSTRAINT ck_bank_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT ck_bank_accounts_currency CHECK (currency IN ('EUR', 'USD', 'GBP')),
    -- A customer account must belong to somebody; a system account must not.
    CONSTRAINT ck_bank_accounts_owner CHECK (
        (account_type = 'CUSTOMER' AND user_id IS NOT NULL)
        OR (account_type = 'SYSTEM' AND user_id IS NULL)
    ),
    -- Last line of defence against overdrawing a customer account: even a bug in the service layer
    -- cannot commit a negative customer balance.
    CONSTRAINT ck_bank_accounts_balance_non_negative CHECK (
        account_type = 'SYSTEM' OR balance >= 0
    )
);

CREATE INDEX idx_bank_accounts_user ON bank_accounts (user_id);
CREATE INDEX idx_bank_accounts_status ON bank_accounts (status);
