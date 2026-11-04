-- What a refresh token needs to become a *session* a customer can recognise and end.
--
-- "Sign out my other devices" is only meaningful if the list says which devices those are. Without
-- these columns the security page could show five identical rows and no way to tell the laptop in
-- the office from the phone that was stolen.
--
-- The user agent is stored, deliberately, in a reduced form: "Chrome on macOS", not the full header.
-- The full string is a fingerprinting surface and the extra precision buys the customer nothing.
ALTER TABLE refresh_tokens
    ADD COLUMN device_label VARCHAR(80),
    ADD COLUMN ip_address   VARCHAR(45),
    ADD COLUMN last_used_at TIMESTAMPTZ;

-- The session list is "mine, still live, most recently used first".
CREATE INDEX idx_refresh_tokens_user_live
    ON refresh_tokens (user_id, last_used_at DESC)
    WHERE revoked_at IS NULL;

COMMENT ON COLUMN refresh_tokens.device_label IS
    'Reduced user agent, e.g. "Chrome on macOS". The raw header is never stored.';

-- Favourite payees. A flag rather than a separate table: it is a property of the saved payee, and
-- the ordering "favourites first, then most recently added" is one index away.
ALTER TABLE beneficiaries
    ADD COLUMN favourite BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_beneficiaries_owner_favourite
    ON beneficiaries (owner_user_id, favourite DESC, created_at DESC)
    WHERE status = 'ACTIVE';
