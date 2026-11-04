-- Monthly spending budgets, one per category.
--
-- Like savings goals, a budget stores only the *limit*. Everything else -- how much is spent, how
-- close the customer is to the cap -- is computed from the transfers that already exist, so there
-- is no counter to keep in step with reality and no backfill to run when a transaction is
-- recategorised.
--
-- The period is fixed at calendar-monthly. Weekly and custom windows are a real product decision
-- rather than a schema one, and inventing columns for them now would only guess wrong.
CREATE TABLE budgets (
    id         UUID           PRIMARY KEY,
    user_id    UUID           NOT NULL,
    category   VARCHAR(20)    NOT NULL,
    amount     NUMERIC(19, 4) NOT NULL,
    currency   VARCHAR(3)     NOT NULL,
    version    BIGINT         NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ    NOT NULL,
    updated_at TIMESTAMPTZ    NOT NULL,

    CONSTRAINT fk_budgets_user FOREIGN KEY (user_id) REFERENCES users (id),

    -- One budget per category per currency. Two budgets for "Groceries" would have no defensible
    -- meaning, and the unique index is what makes "create or update" safe under concurrency.
    CONSTRAINT uq_budgets_user_category UNIQUE (user_id, category, currency),

    CONSTRAINT ck_budgets_amount   CHECK (amount > 0),
    CONSTRAINT ck_budgets_currency CHECK (currency IN ('EUR', 'USD', 'GBP')),
    CONSTRAINT ck_budgets_category CHECK (category IN (
        'GROCERIES', 'DINING', 'SHOPPING', 'TRANSPORT', 'BILLS', 'ENTERTAINMENT',
        'TRAVEL', 'HEALTH', 'EDUCATION', 'SALARY', 'SAVINGS', 'TRANSFER', 'OTHER'
    ))
);

CREATE INDEX idx_budgets_user ON budgets (user_id);

COMMENT ON TABLE budgets IS
    'A monthly cap per category. Spend is derived from transfers, never stored here.';
