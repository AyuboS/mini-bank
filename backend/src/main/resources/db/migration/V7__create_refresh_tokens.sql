-- Refresh tokens are stored as SHA-256 hashes: a database leak must not hand an attacker usable
-- tokens. family_id ties a rotation chain together so that replay of a superseded token can be
-- detected and the whole chain revoked.
CREATE TABLE refresh_tokens (
    id                     UUID        PRIMARY KEY,
    user_id                UUID        NOT NULL,
    token_hash             VARCHAR(64) NOT NULL,
    family_id              UUID        NOT NULL,
    expires_at             TIMESTAMPTZ NOT NULL,
    revoked_at             TIMESTAMPTZ,
    revoked_reason         VARCHAR(40),
    replaced_by_token_hash VARCHAR(64),
    created_at             TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
