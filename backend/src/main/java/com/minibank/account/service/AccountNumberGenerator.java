package com.minibank.account.service;

import com.minibank.account.repository.BankAccountRepository;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Produces customer-facing account numbers of the form {@code MB-XXXXXXXXXXXX}.
 *
 * <p>The digits come from {@link SecureRandom}, not from a database sequence. A sequential number
 * would let anyone who knows one account number guess valid neighbours, which matters because
 * account numbers are shared with third parties in order to receive money — they are identifiers,
 * and they must not double as an enumeration oracle.
 *
 * <p>Uniqueness is still owned by the database: this class retries on collision, and the {@code
 * uq_bank_accounts_number} constraint is the authority.
 */
@Component
public class AccountNumberGenerator {

    private static final String PREFIX = "MB-";
    private static final int DIGITS = 12;
    private static final int MAX_ATTEMPTS = 10;

    private final BankAccountRepository bankAccountRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountNumberGenerator(BankAccountRepository bankAccountRepository) {
        this.bankAccountRepository = bankAccountRepository;
    }

    /**
     * @return an account number that is unused at the time of generation
     * @throws IllegalStateException if no free number is found, which would indicate the keyspace is
     *     effectively exhausted rather than a transient problem
     */
    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = randomAccountNumber();
            if (!bankAccountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not generate a unique account number after " + MAX_ATTEMPTS + " attempts");
    }

    private String randomAccountNumber() {
        StringBuilder builder = new StringBuilder(PREFIX.length() + DIGITS).append(PREFIX);
        for (int i = 0; i < DIGITS; i++) {
            builder.append(secureRandom.nextInt(10));
        }
        return builder.toString();
    }
}
