-- Phone and email verification.
--
-- Existing phone numbers are normalised to E.164 in place before the unique index is added, because
-- the column previously accepted whatever the customer typed. Anything that cannot be normalised
-- (too short to carry a country code, or not digits at all) is cleared rather than guessed at:
-- inventing a country code would attach somebody's account to a number in the wrong country.

ALTER TABLE users ADD COLUMN phone_verified_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;

-- Strip separators, turn a leading 00 into +, and add + to bare digits.
UPDATE users
SET phone_number = CASE
        WHEN regexp_replace(phone_number, '[^0-9+]', '', 'g') LIKE '00%'
            THEN '+' || substring(regexp_replace(phone_number, '[^0-9+]', '', 'g') FROM 3)
        WHEN regexp_replace(phone_number, '[^0-9+]', '', 'g') LIKE '+%'
            THEN regexp_replace(phone_number, '[^0-9+]', '', 'g')
        ELSE '+' || regexp_replace(phone_number, '[^0-9]', '', 'g')
    END
WHERE phone_number IS NOT NULL;

-- Whatever is still not a valid E.164 number was never usable as one.
UPDATE users SET phone_number = NULL
WHERE phone_number IS NOT NULL AND phone_number !~ '^\+[1-9][0-9]{7,14}$';

-- Two accounts cannot share a number: it is a login credential, not a contact detail.
-- Partial, so any number of accounts may have none.
CREATE UNIQUE INDEX uq_users_phone_number ON users (phone_number) WHERE phone_number IS NOT NULL;

ALTER TABLE users ADD CONSTRAINT ck_users_phone_e164
    CHECK (phone_number IS NULL OR phone_number ~ '^\+[1-9][0-9]{7,14}$');

-- The column was 30 characters to hold formatting that is no longer stored.
ALTER TABLE users ALTER COLUMN phone_number TYPE VARCHAR(20);

COMMENT ON COLUMN users.phone_number IS
    'E.164, normalised by the application. Unique when present; usable as a login credential only once phone_verified_at is set.';

-- One verification in progress per destination and purpose.
--
-- The secret is never stored: secret_hash is an HMAC under a server-side key, so a database dump
-- yields nothing usable. A bare digest would not do -- six digits is a space of a million.
CREATE TABLE verification_codes (
    id                UUID         PRIMARY KEY,
    channel           VARCHAR(20)  NOT NULL,
    purpose           VARCHAR(30)  NOT NULL,
    destination       VARCHAR(255) NOT NULL,
    user_id           UUID,
    secret_hash       VARCHAR(64)  NOT NULL,
    expires_at        TIMESTAMPTZ  NOT NULL,
    attempts          INTEGER      NOT NULL DEFAULT 0,
    max_attempts      INTEGER      NOT NULL,
    last_sent_at      TIMESTAMPTZ  NOT NULL,
    consumed_at       TIMESTAMPTZ,
    ticket_hash       VARCHAR(64),
    ticket_expires_at TIMESTAMPTZ,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_verification_codes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    -- The constraint that makes resending safe: a new code replaces the old one instead of adding a
    -- second valid answer. Without it every resend would widen the attacker's target.
    CONSTRAINT uq_verification_codes_destination UNIQUE (channel, destination, purpose),

    CONSTRAINT ck_verification_codes_channel CHECK (channel IN ('PHONE', 'EMAIL')),
    CONSTRAINT ck_verification_codes_purpose CHECK (
        purpose IN ('LOGIN', 'REGISTRATION', 'CONTACT_VERIFICATION')
    ),
    CONSTRAINT ck_verification_codes_attempts CHECK (attempts >= 0 AND attempts <= max_attempts),
    CONSTRAINT ck_verification_codes_max_attempts CHECK (max_attempts > 0),
    -- A ticket without a deadline would never expire.
    CONSTRAINT ck_verification_codes_ticket CHECK (
        (ticket_hash IS NULL AND ticket_expires_at IS NULL)
        OR (ticket_hash IS NOT NULL AND ticket_expires_at IS NOT NULL)
    )
);

-- An email token comes back as a bare secret with no destination attached, so it is looked up by
-- hash alone and needs its own index.
CREATE INDEX idx_verification_codes_secret ON verification_codes (channel, secret_hash);
CREATE INDEX idx_verification_codes_expiry ON verification_codes (expires_at);
CREATE INDEX idx_verification_codes_user ON verification_codes (user_id);

COMMENT ON COLUMN verification_codes.secret_hash IS
    'HMAC-SHA256 of the code or token under a server key. Not reversible and not precomputable.';
COMMENT ON COLUMN verification_codes.ticket_hash IS
    'HMAC of the proof handed out after a successful check, redeemed once when the account is created.';
