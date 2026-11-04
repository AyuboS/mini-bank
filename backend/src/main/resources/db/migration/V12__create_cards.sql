-- Payment cards.
--
-- There is deliberately no column for the verification value. A CVV is not stored anywhere in this
-- schema, in any form: it is derived on demand from a server-side key (see CardCryptoService), which
-- is how an issuer produces one from a card verification key rather than keeping it on file.
--
-- The number itself is held only as AES-GCM ciphertext. `last_four` and `bin` exist so that the
-- ordinary read paths -- lists, dashboards, statements -- never decrypt anything at all, and
-- `pan_fingerprint` is a keyed hash so uniqueness can be enforced without comparing plaintext.
CREATE TABLE cards (
    id               UUID           PRIMARY KEY,
    account_id       UUID           NOT NULL,
    user_id          UUID           NOT NULL,
    label            VARCHAR(50)    NOT NULL,
    card_type        VARCHAR(20)    NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    design           VARCHAR(20)    NOT NULL,
    network          VARCHAR(20)    NOT NULL,
    bin              VARCHAR(6)     NOT NULL,
    last_four        VARCHAR(4)     NOT NULL,
    pan_ciphertext   VARCHAR(512)   NOT NULL,
    pan_fingerprint  VARCHAR(64)    NOT NULL,
    cardholder_name  VARCHAR(120)   NOT NULL,
    expiration_month INTEGER        NOT NULL,
    expiration_year  INTEGER        NOT NULL,
    daily_limit      NUMERIC(19, 4),
    monthly_limit    NUMERIC(19, 4),
    frozen_at        TIMESTAMPTZ,
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,

    CONSTRAINT fk_cards_account FOREIGN KEY (account_id) REFERENCES bank_accounts (id),
    CONSTRAINT fk_cards_user    FOREIGN KEY (user_id)    REFERENCES users (id),

    CONSTRAINT uq_cards_pan_fingerprint UNIQUE (pan_fingerprint),

    CONSTRAINT ck_cards_type    CHECK (card_type IN ('PHYSICAL', 'VIRTUAL')),
    CONSTRAINT ck_cards_status  CHECK (status IN ('ACTIVE', 'FROZEN', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_cards_design  CHECK (design IN ('AURORA', 'MIDNIGHT', 'OCEAN', 'SUNSET',
                                                  'CRYSTAL', 'CARBON', 'HOLOGRAPHIC')),
    CONSTRAINT ck_cards_network CHECK (network IN ('VISA', 'MASTERCARD')),
    CONSTRAINT ck_cards_last_four CHECK (last_four ~ '^[0-9]{4}$'),
    CONSTRAINT ck_cards_expiration_month CHECK (expiration_month BETWEEN 1 AND 12),
    CONSTRAINT ck_cards_expiration_year  CHECK (expiration_year BETWEEN 2000 AND 2100),
    CONSTRAINT ck_cards_daily_limit   CHECK (daily_limit   IS NULL OR daily_limit   > 0),
    CONSTRAINT ck_cards_monthly_limit CHECK (monthly_limit IS NULL OR monthly_limit > 0),
    -- A monthly cap below the daily one would make the daily cap unreachable and is almost
    -- certainly a mistake on the way in rather than an intention.
    CONSTRAINT ck_cards_limit_order CHECK (
        daily_limit IS NULL OR monthly_limit IS NULL OR monthly_limit >= daily_limit
    )
);

CREATE INDEX idx_cards_user ON cards (user_id);
CREATE INDEX idx_cards_account ON cards (account_id);
CREATE INDEX idx_cards_status ON cards (status);

COMMENT ON COLUMN cards.pan_ciphertext IS
    'AES-256-GCM ciphertext of the full number, base64 of iv||ciphertext||tag. Never logged.';
COMMENT ON COLUMN cards.pan_fingerprint IS
    'HMAC-SHA256 of the full number, hex. Used for uniqueness only; not reversible.';
COMMENT ON TABLE cards IS
    'Demo cards. No payment processor is connected and no verification value is stored.';
