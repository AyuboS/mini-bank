-- Support tickets and their message thread.
--
-- A ticket is a conversation, so messages live in their own table rather than being appended into a
-- text column: that keeps authorship, ordering and timestamps intact, and makes "who said what,
-- when" answerable during a dispute.
CREATE TABLE support_tickets (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL,
    reference  VARCHAR(20)  NOT NULL,
    subject    VARCHAR(140) NOT NULL,
    category   VARCHAR(30)  NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_support_tickets_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_support_tickets_reference UNIQUE (reference),

    CONSTRAINT ck_support_tickets_category CHECK (category IN (
        'CARD', 'TRANSFER', 'ACCOUNT', 'SECURITY', 'APP', 'OTHER'
    )),
    CONSTRAINT ck_support_tickets_status CHECK (status IN ('OPEN', 'AWAITING_CUSTOMER', 'RESOLVED', 'CLOSED'))
);

CREATE INDEX idx_support_tickets_user ON support_tickets (user_id, created_at DESC);

CREATE TABLE support_messages (
    id         UUID          PRIMARY KEY,
    ticket_id  UUID          NOT NULL,
    -- NULL when the message came from the bank rather than from a person's account.
    author_id  UUID,
    from_staff BOOLEAN       NOT NULL,
    body       VARCHAR(4000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL,

    CONSTRAINT fk_support_messages_ticket FOREIGN KEY (ticket_id) REFERENCES support_tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_support_messages_author FOREIGN KEY (author_id) REFERENCES users (id)
);

CREATE INDEX idx_support_messages_ticket ON support_messages (ticket_id, created_at);

COMMENT ON TABLE support_messages IS
    'Append-only thread. No updated_at: an edited support message is not a support message.';
