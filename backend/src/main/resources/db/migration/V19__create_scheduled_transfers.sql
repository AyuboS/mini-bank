-- Standing orders: a transfer that runs later, and possibly again.
--
-- This is the table behind both "schedule a transfer" and "recurring payment"; they differ only in
-- frequency, so modelling them separately would duplicate the executor and the pause/resume rules.
--
-- Execution is real. A scheduled job (ScheduledTransferRunner) picks up due rows and puts them
-- through the ordinary TransferService, which means limits, the risk engine, idempotency, the
-- ledger and the audit trail all apply exactly as they do to a transfer somebody typed by hand.
-- Nothing about a due run is simulated.
CREATE TABLE scheduled_transfers (
    id                   UUID           PRIMARY KEY,
    user_id              UUID           NOT NULL,
    source_account_id    UUID           NOT NULL,
    destination_account_number VARCHAR(34) NOT NULL,
    -- Denormalised at creation so the list reads without touching the payee's account row, and so
    -- the name shown stays the one the customer agreed to.
    payee_name           VARCHAR(120),
    amount               NUMERIC(19, 4) NOT NULL,
    currency             VARCHAR(3)     NOT NULL,
    description          VARCHAR(140),
    category             VARCHAR(20),
    frequency            VARCHAR(20)    NOT NULL,
    next_run_on          DATE           NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    last_run_at          TIMESTAMPTZ,
    last_failure_reason  VARCHAR(255),
    runs_completed       INTEGER        NOT NULL DEFAULT 0,
    -- Optional stop condition. NULL means "until cancelled".
    ends_on              DATE,
    version              BIGINT         NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ    NOT NULL,
    updated_at           TIMESTAMPTZ    NOT NULL,

    CONSTRAINT fk_scheduled_transfers_user   FOREIGN KEY (user_id)           REFERENCES users (id),
    CONSTRAINT fk_scheduled_transfers_source FOREIGN KEY (source_account_id) REFERENCES bank_accounts (id),

    CONSTRAINT ck_scheduled_transfers_amount    CHECK (amount > 0),
    CONSTRAINT ck_scheduled_transfers_currency  CHECK (currency IN ('EUR', 'USD', 'GBP')),
    CONSTRAINT ck_scheduled_transfers_frequency CHECK (frequency IN ('ONCE', 'WEEKLY', 'MONTHLY')),
    CONSTRAINT ck_scheduled_transfers_status    CHECK (status IN ('SCHEDULED', 'PAUSED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_scheduled_transfers_category  CHECK (category IS NULL OR category IN (
        'GROCERIES', 'DINING', 'SHOPPING', 'TRANSPORT', 'BILLS', 'ENTERTAINMENT',
        'TRAVEL', 'HEALTH', 'EDUCATION', 'SALARY', 'SAVINGS', 'TRANSFER', 'OTHER'
    ))
);

CREATE INDEX idx_scheduled_transfers_user ON scheduled_transfers (user_id, status);

-- The runner's only query: everything due today or overdue, still live. Partial, because paused,
-- cancelled and finished orders are never candidates and would otherwise bloat the index.
CREATE INDEX idx_scheduled_transfers_due
    ON scheduled_transfers (next_run_on)
    WHERE status = 'SCHEDULED';

COMMENT ON TABLE scheduled_transfers IS
    'Standing orders. Due runs execute through the normal transfer path: limits, risk, ledger, audit.';
