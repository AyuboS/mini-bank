package com.minibank.support;

import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Returns the database to a known empty state between tests.
 *
 * <p>Truncating and re-inserting the system accounts is faster than re-running migrations, and it
 * restores exactly the state migration V10 leaves behind, so tests see the same starting point the
 * application does.
 */
@Component
public class DatabaseCleaner {

    /** Child tables first; CASCADE would work but naming them keeps the dependency order explicit. */
    private static final String TRUNCATE =
            """
            TRUNCATE TABLE support_messages, support_tickets, notifications, budgets,
                           money_requests, scheduled_transfers, savings_goals,
                           ledger_entries, transfers, idempotency_records, refresh_tokens,
                           audit_logs, beneficiaries, transfer_limit_overrides,
                           verification_codes, cards, bank_accounts, users
            RESTART IDENTITY CASCADE
            """;

    /*
     * `purpose` is stated explicitly rather than left to its column default: the point of this
     * class is to restore exactly what migration V10 leaves behind, and a default is a fact about
     * the schema today rather than a promise about the row.
     */
    private static final String RESTORE_SYSTEM_ACCOUNTS =
            """
            INSERT INTO bank_accounts (id, account_number, user_id, account_type, purpose, currency,
                                       status, balance, version, created_at, updated_at)
            VALUES (gen_random_uuid(), 'MB-SYSTEM-EUR', NULL, 'SYSTEM', 'SPENDING', 'EUR', 'ACTIVE', 0, 0, NOW(), NOW()),
                   (gen_random_uuid(), 'MB-SYSTEM-USD', NULL, 'SYSTEM', 'SPENDING', 'USD', 'ACTIVE', 0, 0, NOW(), NOW()),
                   (gen_random_uuid(), 'MB-SYSTEM-GBP', NULL, 'SYSTEM', 'SPENDING', 'GBP', 'ACTIVE', 0, 0, NOW(), NOW())
            """;

    private final JdbcTemplate jdbcTemplate;

    public DatabaseCleaner(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public void reset() {
        jdbcTemplate.execute(TRUNCATE);
        jdbcTemplate.execute(RESTORE_SYSTEM_ACCOUNTS);
    }

    /** Reads a balance straight from SQL, bypassing JPA's first-level cache. */
    public java.math.BigDecimal balanceOf(String accountNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM bank_accounts WHERE account_number = ?",
                java.math.BigDecimal.class,
                accountNumber);
    }

    public int countRows(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
