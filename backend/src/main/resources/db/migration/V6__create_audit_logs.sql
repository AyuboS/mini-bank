-- Append-only security and business audit trail.
-- The username is denormalised so that failed logins for a non-existent user are still recorded.
CREATE TABLE audit_logs (
    id          UUID        PRIMARY KEY,
    user_id     UUID,
    username    VARCHAR(50),
    action      VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id   VARCHAR(64),
    outcome     VARCHAR(10) NOT NULL,
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(255),
    request_id  VARCHAR(64),
    metadata    JSONB,
    created_at  TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_audit_logs_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX idx_audit_logs_user_created ON audit_logs (user_id, created_at DESC);
CREATE INDEX idx_audit_logs_action_created ON audit_logs (action, created_at DESC);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC);

COMMENT ON TABLE audit_logs IS
    'Append-only. No application code path updates or deletes rows in this table.';
