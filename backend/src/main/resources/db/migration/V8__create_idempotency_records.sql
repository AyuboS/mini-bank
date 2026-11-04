-- Idempotency keys are scoped per user and per endpoint, so one customer can never collide with
-- (or observe) another customer's key. request_hash lets the API reject a key that is replayed
-- with a different payload instead of silently returning the wrong result.
CREATE TABLE idempotency_records (
    id              UUID         PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL,
    user_id         UUID         NOT NULL,
    endpoint        VARCHAR(120) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    response_status INTEGER,
    response_body   TEXT,
    resource_id     UUID,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uq_idempotency_scope UNIQUE (user_id, endpoint, idempotency_key),
    CONSTRAINT fk_idempotency_user  FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_idempotency_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_idempotency_created_at ON idempotency_records (created_at);
