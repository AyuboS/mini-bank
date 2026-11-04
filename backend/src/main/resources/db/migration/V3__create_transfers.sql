-- A transfer is the business level record of a money movement. The authoritative accounting
-- record lives in ledger_entries; this table carries workflow state, risk outcome and metadata.
CREATE TABLE transfers (
    id                      UUID           PRIMARY KEY,
    reference               VARCHAR(32)    NOT NULL,
    idempotency_key         VARCHAR(100),
    transfer_type           VARCHAR(20)    NOT NULL,
    source_account_id       UUID           NOT NULL,
    destination_account_id  UUID           NOT NULL,
    initiated_by_user_id    UUID,
    amount                  NUMERIC(19, 4) NOT NULL,
    currency                VARCHAR(3)     NOT NULL,
    status                  VARCHAR(20)    NOT NULL,
    description             VARCHAR(140),
    failure_reason          VARCHAR(255),
    risk_decision           VARCHAR(10),
    risk_score              INTEGER,
    reversal_of_transfer_id UUID,
    completed_at            TIMESTAMPTZ,
    version                 BIGINT         NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ    NOT NULL,
    updated_at              TIMESTAMPTZ    NOT NULL,

    CONSTRAINT uq_transfers_reference UNIQUE (reference),
    CONSTRAINT fk_transfers_source      FOREIGN KEY (source_account_id) REFERENCES bank_accounts (id),
    CONSTRAINT fk_transfers_destination FOREIGN KEY (destination_account_id) REFERENCES bank_accounts (id),
    CONSTRAINT fk_transfers_initiator   FOREIGN KEY (initiated_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_transfers_reversal_of FOREIGN KEY (reversal_of_transfer_id) REFERENCES transfers (id),
    CONSTRAINT ck_transfers_type CHECK (transfer_type IN ('INTERNAL', 'DEPOSIT', 'WITHDRAWAL', 'REVERSAL')),
    CONSTRAINT ck_transfers_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REVERSED')),
    CONSTRAINT ck_transfers_currency CHECK (currency IN ('EUR', 'USD', 'GBP')),
    CONSTRAINT ck_transfers_risk_decision CHECK (
        risk_decision IS NULL OR risk_decision IN ('ALLOW', 'REVIEW', 'BLOCK')
    ),
    CONSTRAINT ck_transfers_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transfers_distinct_accounts CHECK (source_account_id <> destination_account_id),
    -- A settled transfer must record when it settled; anything else must not pretend it did.
    CONSTRAINT ck_transfers_completed_at CHECK (
        (status IN ('COMPLETED', 'REVERSED') AND completed_at IS NOT NULL)
        OR (status NOT IN ('COMPLETED', 'REVERSED') AND completed_at IS NULL)
    )
);

CREATE INDEX idx_transfers_source_created ON transfers (source_account_id, created_at DESC);
CREATE INDEX idx_transfers_destination_created ON transfers (destination_account_id, created_at DESC);
CREATE INDEX idx_transfers_initiator_created ON transfers (initiated_by_user_id, created_at DESC);
CREATE INDEX idx_transfers_status ON transfers (status);
CREATE INDEX idx_transfers_created_at ON transfers (created_at DESC);
