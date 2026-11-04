-- Per-customer limit overrides. When no row exists the platform defaults from application
-- configuration apply, so the common case needs no data at all.
CREATE TABLE transfer_limit_overrides (
    id                     UUID           PRIMARY KEY,
    user_id                UUID           NOT NULL,
    per_transfer_max       NUMERIC(19, 4),
    daily_max              NUMERIC(19, 4),
    max_transfers_per_hour INTEGER,
    note                   VARCHAR(255),
    version                BIGINT         NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ    NOT NULL,
    updated_at             TIMESTAMPTZ    NOT NULL,

    CONSTRAINT uq_transfer_limit_overrides_user UNIQUE (user_id),
    CONSTRAINT fk_transfer_limit_overrides_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_transfer_limit_per_transfer CHECK (per_transfer_max IS NULL OR per_transfer_max > 0),
    CONSTRAINT ck_transfer_limit_daily CHECK (daily_max IS NULL OR daily_max > 0),
    CONSTRAINT ck_transfer_limit_hourly CHECK (max_transfers_per_hour IS NULL OR max_transfers_per_hour > 0)
);
