-- Per-card usage controls and the two caps that were missing.
--
-- These are switches a cardholder expects to find in a modern banking app: turn off online payments
-- while the card sits in a drawer, turn off international use before a card is lost abroad. They
-- are enforced server-side (CardControlService), because a control that only hides a button is not
-- a control.
--
-- Magnetic stripe defaults to OFF. Every other channel defaults to ON, matching the least
-- surprising behaviour for a newly issued card; the stripe is the one channel with no cryptographic
-- protection, so it is opt-in rather than opt-out.
ALTER TABLE cards
    ADD COLUMN online_payments_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN contactless_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN atm_withdrawals_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN international_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN magnetic_stripe_enabled  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN atm_limit                NUMERIC(19, 4),
    ADD COLUMN online_limit             NUMERIC(19, 4);

ALTER TABLE cards
    ADD CONSTRAINT ck_cards_atm_limit    CHECK (atm_limit    IS NULL OR atm_limit    > 0),
    ADD CONSTRAINT ck_cards_online_limit CHECK (online_limit IS NULL OR online_limit > 0);

COMMENT ON COLUMN cards.magnetic_stripe_enabled IS
    'Off by default: the stripe is the only channel with no cryptographic authentication.';
COMMENT ON COLUMN cards.atm_limit IS
    'Per-day cash withdrawal cap. NULL means the daily card limit is the only cap that applies.';
