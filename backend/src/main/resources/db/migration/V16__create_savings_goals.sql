-- Savings goals.
--
-- A goal is backed by a real bank account of its own, and contributing to it performs a real
-- internal transfer. That is the whole design decision: there is no `current_amount` column, because
-- progress is simply the balance of the backing account. A denormalised total would be one more
-- number that can drift away from the ledger, and a bank whose savings screen disagrees with its
-- ledger is worse than one with no savings screen.
--
-- Withdrawing is the same movement in reverse, so no special "release the money" path exists either.

-- What an account is for. Existing accounts are spending accounts; that is what they have always
-- been, so the default backfills correctly.
ALTER TABLE bank_accounts
    ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'SPENDING';

ALTER TABLE bank_accounts
    ADD CONSTRAINT ck_bank_accounts_purpose CHECK (purpose IN ('SPENDING', 'SAVINGS'));

CREATE TABLE savings_goals (
    id            UUID          PRIMARY KEY,
    user_id       UUID          NOT NULL,
    account_id    UUID          NOT NULL,
    name          VARCHAR(60)   NOT NULL,
    target_amount NUMERIC(19, 4) NOT NULL,
    currency      VARCHAR(3)    NOT NULL,
    deadline      DATE,
    status        VARCHAR(20)   NOT NULL,
    accent        VARCHAR(20)   NOT NULL,
    version       BIGINT        NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,

    CONSTRAINT fk_savings_goals_user    FOREIGN KEY (user_id)    REFERENCES users (id),
    CONSTRAINT fk_savings_goals_account FOREIGN KEY (account_id) REFERENCES bank_accounts (id),

    -- One goal per backing account, in both directions: an account funds exactly one goal.
    CONSTRAINT uq_savings_goals_account UNIQUE (account_id),

    CONSTRAINT ck_savings_goals_target   CHECK (target_amount > 0),
    CONSTRAINT ck_savings_goals_currency CHECK (currency IN ('EUR', 'USD', 'GBP')),
    CONSTRAINT ck_savings_goals_status   CHECK (status IN ('ACTIVE', 'ACHIEVED', 'ARCHIVED'))
);

CREATE INDEX idx_savings_goals_user ON savings_goals (user_id, status);

COMMENT ON TABLE savings_goals IS
    'A named target over a dedicated account. Progress is the account balance, never a stored total.';
