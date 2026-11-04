-- The risk engine's verdict was already recorded as a decision and a score, but the reasons behind
-- it lived only in the audit log. An administrator reviewing a held transfer needs to see why it
-- was held, next to the transfer itself, so the explanation is stored alongside the outcome.
ALTER TABLE transfers ADD COLUMN risk_reasons JSONB;

COMMENT ON COLUMN transfers.risk_reasons IS
    'Human readable explanations from the rules that fired, as a JSON array of strings.';
