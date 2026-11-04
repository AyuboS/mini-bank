-- Saved payees. Deletion is soft, so that the history of who a customer used to pay is preserved
-- for the risk engine and for audit.
CREATE TABLE beneficiaries (
    id                         UUID        PRIMARY KEY,
    owner_user_id              UUID        NOT NULL,
    beneficiary_account_number VARCHAR(20) NOT NULL,
    nickname                   VARCHAR(60) NOT NULL,
    status                     VARCHAR(20) NOT NULL,
    version                    BIGINT      NOT NULL DEFAULT 0,
    created_at                 TIMESTAMPTZ NOT NULL,
    updated_at                 TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_beneficiaries_owner FOREIGN KEY (owner_user_id) REFERENCES users (id),
    CONSTRAINT ck_beneficiaries_status CHECK (status IN ('ACTIVE', 'DELETED'))
);

-- A partial unique index allows the same payee to be re-added after a soft delete, while still
-- preventing duplicate active entries.
CREATE UNIQUE INDEX uq_beneficiaries_owner_account_active
    ON beneficiaries (owner_user_id, beneficiary_account_number)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_beneficiaries_owner ON beneficiaries (owner_user_id);
