-- The in-app notification centre.
--
-- Distinct from the `notification` delivery package, which sends SMS and email. This table is the
-- feed a customer sees inside the app: what happened to their money, their cards and their session.
--
-- Rows are written by the same transaction that performs the underlying action, so a notification
-- cannot exist for a transfer that was rolled back, and a settled transfer cannot silently produce
-- no notification.
CREATE TABLE notifications (
    id           UUID         PRIMARY KEY,
    user_id      UUID         NOT NULL,
    type         VARCHAR(20)  NOT NULL,
    severity     VARCHAR(20)  NOT NULL,
    title        VARCHAR(120) NOT NULL,
    body         VARCHAR(400) NOT NULL,
    -- What the notification is about, so the client can deep-link. Deliberately not a foreign key:
    -- the target may be any of several tables, and a notification must survive its subject being
    -- archived.
    entity_type  VARCHAR(40),
    entity_id    UUID,
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),

    CONSTRAINT ck_notifications_type CHECK (type IN ('TRANSACTION', 'SECURITY', 'CARD', 'BUDGET', 'SYSTEM')),
    CONSTRAINT ck_notifications_severity CHECK (severity IN ('INFO', 'SUCCESS', 'WARNING', 'CRITICAL'))
);

-- The feed query is always "this user's, newest first"; the partial index serves the unread badge,
-- which is requested on every page load and must not scan the whole feed.
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;

COMMENT ON TABLE notifications IS
    'In-app feed. Written inside the transaction that caused the event, so the two cannot disagree.';
