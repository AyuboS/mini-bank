package com.minibank.risk;

import com.minibank.account.domain.BankAccount;
import com.minibank.common.money.CurrencyCode;
import com.minibank.risk.config.RiskProperties;
import com.minibank.risk.domain.RiskContext;
import com.minibank.user.domain.User;
import com.minibank.user.domain.UserRole;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Shared builders so each risk rule test states only what it actually varies. */
public final class RiskTestFixtures {

    public static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");

    private RiskTestFixtures() {}

    public static RiskProperties defaultProperties() {
        return new RiskProperties(
                new RiskProperties.LargeAmount(new BigDecimal("2500.00"), 35),
                new RiskProperties.Velocity(Duration.ofMinutes(5), 5, 30),
                new RiskProperties.UnusualAmount(
                        Duration.ofDays(30), new BigDecimal("5"), new BigDecimal("250.00"), 25),
                new RiskProperties.FailedLogins(Duration.ofMinutes(30), 3, 20),
                new RiskProperties.NewBeneficiary(Duration.ofMinutes(60), new BigDecimal("1000.00"), 30),
                new RiskProperties.Thresholds(50, 85));
    }

    public static User user() {
        User user = User.create("john", "john@example.com", "hash", "John", "Smith", UserRole.CUSTOMER);
        user.setId(UUID.randomUUID());
        return user;
    }

    public static BankAccount account(String number, String balance) {
        BankAccount account = BankAccount.openCustomerAccount(null, number, CurrencyCode.EUR);
        account.setId(UUID.randomUUID());
        account.setBalance(new BigDecimal(balance));
        return account;
    }

    public static RiskContext context(User user, BankAccount source, BankAccount destination, String amount) {
        return new RiskContext(user, source, destination, new BigDecimal(amount), CurrencyCode.EUR, NOW);
    }

    public static RiskContext context(String amount) {
        return context(user(), account("MB-000000000001", "10000"), account("MB-000000000002", "0"), amount);
    }
}
