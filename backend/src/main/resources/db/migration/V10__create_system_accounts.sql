-- One funding account per supported currency. These are the counterparty for every deposit and
-- withdrawal, which is what allows the ledger to stay balanced: money is never created out of
-- nothing, it moves from the bank's own position to a customer's.
INSERT INTO bank_accounts (
    id, account_number, user_id, account_type, currency, status, balance, version, created_at, updated_at
) VALUES
    (gen_random_uuid(), 'MB-SYSTEM-EUR', NULL, 'SYSTEM', 'EUR', 'ACTIVE', 0, 0, NOW(), NOW()),
    (gen_random_uuid(), 'MB-SYSTEM-USD', NULL, 'SYSTEM', 'USD', 'ACTIVE', 0, 0, NOW(), NOW()),
    (gen_random_uuid(), 'MB-SYSTEM-GBP', NULL, 'SYSTEM', 'GBP', 'ACTIVE', 0, 0, NOW(), NOW());
