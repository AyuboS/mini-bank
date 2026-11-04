package com.minibank.support;

import com.minibank.account.domain.BankAccount;
import com.minibank.account.service.AccountService;
import com.minibank.common.money.CurrencyCode;
import com.minibank.transfer.domain.TransactionCategory;
import com.minibank.transfer.domain.TransferType;
import com.minibank.transfer.service.TransferExecutor;
import com.minibank.user.domain.User;
import com.minibank.user.domain.UserRole;
import com.minibank.user.repository.UserRepository;
import java.math.BigDecimal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the users, accounts and opening balances a test needs.
 *
 * <p>Accounts are funded through {@link TransferExecutor} rather than by writing a balance directly,
 * so the ledger invariant holds from the first row. A test that set balances by hand would then
 * "fail" the reconciliation assertions for reasons that have nothing to do with the code.
 */
@Component
public class TestDataFactory {

    /** Satisfies the password policy; used for every test user so logins are predictable. */
    public static final String PASSWORD = "TestPassword123!";

    private final UserRepository userRepository;
    private final AccountService accountService;
    private final TransferExecutor transferExecutor;
    private final PasswordEncoder passwordEncoder;

    public TestDataFactory(
            UserRepository userRepository,
            AccountService accountService,
            TransferExecutor transferExecutor,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.accountService = accountService;
        this.transferExecutor = transferExecutor;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User customer(String username) {
        return createUser(username, UserRole.CUSTOMER);
    }

    @Transactional
    public User admin(String username) {
        return createUser(username, UserRole.ADMIN);
    }

    private User createUser(String username, UserRole role) {
        return userRepository.save(User.create(
                username,
                username + "@example.com",
                passwordEncoder.encode(PASSWORD),
                "Test",
                "User" + Math.abs(username.hashCode() % 1000),
                role));
    }

    @Transactional
    public BankAccount account(User owner, CurrencyCode currency) {
        return accountService.openAccount(owner, currency);
    }

    /** Opens a EUR account and funds it from the bank's system account. */
    @Transactional
    public BankAccount fundedAccount(User owner, String openingBalance) {
        BankAccount account = accountService.openAccount(owner, CurrencyCode.EUR);
        if (new BigDecimal(openingBalance).signum() > 0) {
            fund(account, openingBalance, operator());
        }
        return account;
    }

    /**
     * The account the opening-balance deposits are attributed to.
     *
     * <p>Deliberately not the customer: a deposit initiated by the customer would count towards
     * their own transfer velocity and daily total, and tests about risk or limits would then be
     * measuring setup rather than the behaviour they claim to test.
     */
    @Transactional
    public User operator() {
        return userRepository.findByUsername("test.operator").orElseGet(() -> createUser("test.operator", UserRole.ADMIN));
    }

    @Transactional
    public void fund(BankAccount account, String amount, User initiator) {
        BankAccount system = accountService.getSystemAccount(account.getCurrency());
        transferExecutor.settle(new TransferExecutor.SettlementCommand(
                system.getId(),
                account.getId(),
                new BigDecimal(amount),
                initiator,
                "Test opening balance",
                null,
                TransferType.DEPOSIT,
                TransactionCategory.TRANSFER,
                null,
                0,
                java.util.List.of()));
    }
}
