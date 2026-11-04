-- Core identity table. Usernames and emails are stored already normalised to lower case by the
-- application, so a plain unique constraint is enough to make lookups case-insensitive.
CREATE TABLE users (
    id                    UUID         PRIMARY KEY,
    username              VARCHAR(50)  NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    first_name            VARCHAR(100) NOT NULL,
    last_name             VARCHAR(100) NOT NULL,
    phone_number          VARCHAR(30),
    status                VARCHAR(20)  NOT NULL,
    role                  VARCHAR(20)  NOT NULL,
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    lockout_until         TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email    UNIQUE (email),
    CONSTRAINT ck_users_status   CHECK (status IN ('ACTIVE', 'LOCKED', 'SUSPENDED', 'DELETED')),
    CONSTRAINT ck_users_role     CHECK (role IN ('CUSTOMER', 'ADMIN')),
    CONSTRAINT ck_users_failed_login_attempts CHECK (failed_login_attempts >= 0)
);

COMMENT ON COLUMN users.password_hash IS 'BCrypt hash. Plain text passwords are never stored or logged.';
