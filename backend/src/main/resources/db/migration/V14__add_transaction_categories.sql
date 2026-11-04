-- Spending categories on transfers.
--
-- Kept on the transfer rather than on the ledger entry: a category describes the *reason* money
-- moved, which is a property of the payment, not of either side's bookkeeping. Both counterparties
-- therefore see the same label, which is what makes "€48.20, Groceries" mean the same thing to the
-- payer and to a statement.
--
-- Nullable on purpose. Rows written before this migration have no category and must not be
-- retroactively guessed; the API reports them as uncategorised and the owner may set one.
ALTER TABLE transfers
    ADD COLUMN category VARCHAR(20);

ALTER TABLE transfers
    ADD CONSTRAINT ck_transfers_category CHECK (
        category IS NULL OR category IN (
            'GROCERIES', 'DINING', 'SHOPPING', 'TRANSPORT', 'BILLS', 'ENTERTAINMENT',
            'TRAVEL', 'HEALTH', 'EDUCATION', 'SALARY', 'SAVINGS', 'TRANSFER', 'OTHER'
        )
    );

-- Analytics group by (account, month, category); without this the category breakdown degenerates
-- into a sequential scan of the whole transfer table once history grows.
CREATE INDEX idx_transfers_source_created_category
    ON transfers (source_account_id, created_at DESC, category);

COMMENT ON COLUMN transfers.category IS
    'Spending category. NULL means uncategorised; never inferred retroactively.';
