-- "Request money": an invitation to pay, which the payer settles with a real transfer.
--
-- The request itself moves nothing. Only the payer can act on it, and when they do, the money moves
-- through TransferService like any other payment -- the request row simply records which transfer
-- settled it. Modelling it the other way round (a request that pulls funds) would amount to letting
-- one customer debit another, which no amount of UI can make acceptable.
CREATE TABLE money_requests (
    id                    UUID           PRIMARY KEY,
    requester_user_id     UUID           NOT NULL,
    -- Where the money should land if the request is paid.
    requester_account_id  UUID           NOT NULL,
    -- Who is being asked. Stored as an account number because a request may be sent before the
    -- payer is known to be a customer.
    payer_account_number  VARCHAR(34)    NOT NULL,
    amount                NUMERIC(19, 4) NOT NULL,
    currency              VARCHAR(3)     NOT NULL,
    description           VARCHAR(140),
    status                VARCHAR(20)    NOT NULL,
    expires_at            TIMESTAMPTZ    NOT NULL,
    settled_transfer_id   UUID,
    settled_at            TIMESTAMPTZ,
    version               BIGINT         NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL,
    updated_at            TIMESTAMPTZ    NOT NULL,

    CONSTRAINT fk_money_requests_user     FOREIGN KEY (requester_user_id)    REFERENCES users (id),
    CONSTRAINT fk_money_requests_account  FOREIGN KEY (requester_account_id) REFERENCES bank_accounts (id),
    CONSTRAINT fk_money_requests_transfer FOREIGN KEY (settled_transfer_id)  REFERENCES transfers (id),

    CONSTRAINT ck_money_requests_amount   CHECK (amount > 0),
    CONSTRAINT ck_money_requests_currency CHECK (currency IN ('EUR', 'USD', 'GBP')),
    CONSTRAINT ck_money_requests_status   CHECK (status IN ('PENDING', 'PAID', 'DECLINED', 'EXPIRED', 'CANCELLED')),

    -- A paid request must say what paid it. Enforced here rather than in Java so that no code path,
    -- present or future, can mark one settled without the evidence.
    CONSTRAINT ck_money_requests_settled CHECK (
        (status = 'PAID') = (settled_transfer_id IS NOT NULL)
    )
);

CREATE INDEX idx_money_requests_requester ON money_requests (requester_user_id, status);
CREATE INDEX idx_money_requests_payer     ON money_requests (payer_account_number, status);

COMMENT ON TABLE money_requests IS
    'An invitation to pay. Settling it is an ordinary transfer initiated by the payer, never a pull.';
