-- Append-only double-entry ledger: the authoritative record of every movement of money.
-- bank_accounts.balance is a derived cache maintained in the same transaction as these rows.
CREATE TABLE ledger_entries (
    id            UUID           PRIMARY KEY,
    transfer_id   UUID           NOT NULL,
    account_id    UUID           NOT NULL,
    entry_type    VARCHAR(6)     NOT NULL,
    amount        NUMERIC(19, 4) NOT NULL,
    currency      VARCHAR(3)     NOT NULL,
    balance_after NUMERIC(19, 4) NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL,

    CONSTRAINT fk_ledger_entries_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (id),
    CONSTRAINT fk_ledger_entries_account  FOREIGN KEY (account_id) REFERENCES bank_accounts (id),
    CONSTRAINT ck_ledger_entries_type     CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_entries_currency CHECK (currency IN ('EUR', 'USD', 'GBP')),
    CONSTRAINT ck_ledger_entries_amount_positive CHECK (amount > 0),
    -- One posting per side per transfer: a retry can never double-post the same leg.
    CONSTRAINT uq_ledger_entries_transfer_leg UNIQUE (transfer_id, account_id, entry_type)
);

CREATE INDEX idx_ledger_entries_account_created ON ledger_entries (account_id, created_at DESC);
CREATE INDEX idx_ledger_entries_transfer ON ledger_entries (transfer_id);

COMMENT ON TABLE ledger_entries IS
    'Append-only. Rows are never updated or deleted; corrections are made with compensating entries.';
