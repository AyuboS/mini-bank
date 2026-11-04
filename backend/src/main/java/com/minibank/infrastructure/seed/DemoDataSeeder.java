package com.minibank.infrastructure.seed;

import com.minibank.account.domain.AccountPurpose;
import com.minibank.account.domain.BankAccount;
import com.minibank.account.service.AccountService;
import com.minibank.budget.domain.Budget;
import com.minibank.budget.repository.BudgetRepository;
import com.minibank.card.domain.Card;
import com.minibank.card.domain.CardDesign;
import com.minibank.card.domain.CardNetwork;
import com.minibank.card.domain.CardType;
import com.minibank.card.repository.CardRepository;
import com.minibank.card.service.CardCryptoService;
import com.minibank.card.service.CardNumberGenerator;
import com.minibank.common.money.CurrencyCode;
import com.minibank.savings.domain.SavingsGoal;
import com.minibank.savings.repository.SavingsGoalRepository;
import com.minibank.transfer.domain.TransactionCategory;
import com.minibank.transfer.domain.TransferType;
import com.minibank.transfer.service.TransferExecutor;
import com.minibank.user.domain.User;
import com.minibank.user.domain.UserRole;
import com.minibank.user.repository.UserRepository;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the demo users, accounts and opening balances documented in the README.
 *
 * <p>Idempotent: it checks for the admin account and does nothing if the data is already there, so
 * restarting the stack does not duplicate anything or reset balances a demo has changed.
 *
 * <p>Opening balances are funded through {@link TransferExecutor}, not by writing a balance column
 * directly. That matters: a hand-set balance would have no ledger entries behind it, and the
 * reconciliation invariant "balance equals the sum of the account's ledger entries" would be broken
 * from the very first row.
 *
 * <p>The credentials below are obviously non-production and are printed at startup precisely so that
 * nobody mistakes them for real ones.
 */
@Component
@ConditionalOnProperty(prefix = "minibank.seed", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "Admin#2026!Demo";
    private static final String CUSTOMER_A_USERNAME = "customer.a";
    private static final String CUSTOMER_A_PASSWORD = "Customer#2026!A";
    private static final String CUSTOMER_B_USERNAME = "customer.b";
    private static final String CUSTOMER_B_PASSWORD = "Customer#2026!B";

    /**
     * Demo phone numbers, in the +99 range that E.164 reserves and no country uses. They are
     * marked verified so the phone sign-in flow can be tried; with the development SMS provider the
     * code is printed to this log rather than sent anywhere.
     */
    private static final String CUSTOMER_A_PHONE = "+999700000001";
    private static final String CUSTOMER_B_PHONE = "+999700000002";

    private final UserRepository userRepository;
    private final AccountService accountService;
    private final TransferExecutor transferExecutor;
    private final PasswordEncoder passwordEncoder;
    private final CardRepository cardRepository;
    private final CardNumberGenerator cardNumberGenerator;
    private final CardCryptoService cardCrypto;
    private final BudgetRepository budgetRepository;
    private final SavingsGoalRepository savingsGoalRepository;
    private final java.time.Clock clock;

    public DemoDataSeeder(
            UserRepository userRepository,
            AccountService accountService,
            TransferExecutor transferExecutor,
            PasswordEncoder passwordEncoder,
            CardRepository cardRepository,
            CardNumberGenerator cardNumberGenerator,
            CardCryptoService cardCrypto,
            BudgetRepository budgetRepository,
            SavingsGoalRepository savingsGoalRepository,
            java.time.Clock clock) {
        this.userRepository = userRepository;
        this.accountService = accountService;
        this.transferExecutor = transferExecutor;
        this.passwordEncoder = passwordEncoder;
        this.cardRepository = cardRepository;
        this.cardNumberGenerator = cardNumberGenerator;
        this.cardCrypto = cardCrypto;
        this.budgetRepository = budgetRepository;
        this.savingsGoalRepository = savingsGoalRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(ADMIN_USERNAME)) {
            log.info("Demo data already present, skipping seeding");
            return;
        }

        User admin = createUser(ADMIN_USERNAME, "admin@minibank.local", ADMIN_PASSWORD, "Ada", "Admin", UserRole.ADMIN);
        User customerA = createUser(
                CUSTOMER_A_USERNAME, "alice@example.com", CUSTOMER_A_PASSWORD, "Alice", "Johnson", UserRole.CUSTOMER);
        User customerB = createUser(
                CUSTOMER_B_USERNAME, "bob@example.com", CUSTOMER_B_PASSWORD, "Bob", "Miller", UserRole.CUSTOMER);

        BankAccount adminAccount = accountService.openAccount(admin, CurrencyCode.EUR);
        BankAccount accountA = accountService.openAccount(customerA, CurrencyCode.EUR);
        BankAccount accountB = accountService.openAccount(customerB, CurrencyCode.EUR);

        fund(accountA, new BigDecimal("4250.5000"), admin, "Opening balance");
        fund(accountB, new BigDecimal("800.0000"), admin, "Opening balance");

        customerA.confirmPhoneNumber(CUSTOMER_A_PHONE, clock.instant());
        customerB.confirmPhoneNumber(CUSTOMER_B_PHONE, clock.instant());

        // A wallet worth looking at on first run: two designs, one of each type.
        issueCard(accountA, customerA, CardType.PHYSICAL, CardDesign.AURORA, CardNetwork.VISA, "Everyday");
        issueCard(accountA, customerA, CardType.VIRTUAL, CardDesign.HOLOGRAPHIC, CardNetwork.VISA, "Online");
        issueCard(accountB, customerB, CardType.PHYSICAL, CardDesign.MIDNIGHT, CardNetwork.MASTERCARD, "Everyday");

        seedSpendingHistory(accountA, customerA, admin);
        seedBudgets(customerA);
        seedSavingsGoal(customerA, accountA, admin);

        log.info(
                """

                ==========================================================================
                 MBank demo data created. These credentials are for demonstration only.
                --------------------------------------------------------------------------
                  ADMIN     {} / {}
                  CUSTOMER  {} / {}   account {}  (funded, with a month of activity)
                  CUSTOMER  {} / {}   account {}  balance EUR 800.00
                --------------------------------------------------------------------------
                 Admin's own account: {}
                 Phone sign-in:       {} and {} (verified; the code is printed above)
                ==========================================================================
                """,
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                CUSTOMER_A_USERNAME,
                CUSTOMER_A_PASSWORD,
                accountA.getAccountNumber(),
                CUSTOMER_B_USERNAME,
                CUSTOMER_B_PASSWORD,
                accountB.getAccountNumber(),
                adminAccount.getAccountNumber(),
                CUSTOMER_A_PHONE,
                CUSTOMER_B_PHONE);
    }

    /**
     * A month of realistic movement on the primary demo account.
     *
     * <p>Exists so the analytics, budget and statement screens have something true to display on a
     * fresh install. Every row is a real settled transfer through the ledger — the demo is seeded
     * with transactions, not with pre-computed charts, so what those screens show is arithmetic over
     * the same data a real customer would generate.
     *
     * <p>Money leaves to the bank's own system account, which is what a card purchase looks like in
     * a system with no card scheme attached: the funds genuinely leave the customer's balance.
     */
    private void seedSpendingHistory(BankAccount account, User owner, User initiator) {
        fund(account, new BigDecimal("2400.0000"), initiator, "Salary — August", TransactionCategory.SALARY);

        spend(account, owner, "48.20", "Grocery store", TransactionCategory.GROCERIES);
        spend(account, owner, "62.40", "Weekly shop", TransactionCategory.GROCERIES);
        spend(account, owner, "71.10", "Supermarket", TransactionCategory.GROCERIES);
        spend(account, owner, "15.99", "Netflix", TransactionCategory.ENTERTAINMENT);
        spend(account, owner, "11.99", "Spotify", TransactionCategory.ENTERTAINMENT);
        spend(account, owner, "34.50", "Dinner with friends", TransactionCategory.DINING);
        spend(account, owner, "18.80", "Coffee and lunch", TransactionCategory.DINING);
        spend(account, owner, "820.00", "Rent — August", TransactionCategory.BILLS);
        spend(account, owner, "42.00", "Internet", TransactionCategory.BILLS);
        spend(account, owner, "28.60", "Taxi", TransactionCategory.TRANSPORT);
        spend(account, owner, "60.00", "Fuel", TransactionCategory.TRANSPORT);
        spend(account, owner, "129.00", "New shoes", TransactionCategory.SHOPPING);
    }

    /** Two budgets, set where the seeded spending will land near but not past the cap. */
    private void seedBudgets(User owner) {
        budgetRepository.save(
                Budget.of(owner, TransactionCategory.GROCERIES, new BigDecimal("300.0000"), CurrencyCode.EUR));
        budgetRepository.save(
                Budget.of(owner, TransactionCategory.DINING, new BigDecimal("150.0000"), CurrencyCode.EUR));
    }

    /**
     * One savings goal, part funded.
     *
     * <p>Opened through the same path the API uses, so it has a real backing account and its
     * progress is a real balance rather than a seeded number.
     */
    private void seedSavingsGoal(User owner, BankAccount from, User initiator) {
        BankAccount goalAccount = accountService.openAccount(owner, CurrencyCode.EUR, AccountPurpose.SAVINGS);
        SavingsGoal goal = savingsGoalRepository.save(SavingsGoal.open(
                owner,
                goalAccount,
                "Thailand trip",
                new BigDecimal("1500.0000"),
                java.time.LocalDate.ofInstant(clock.instant(), java.time.ZoneOffset.UTC).plusMonths(6),
                "cyan"));

        transferExecutor.settle(new TransferExecutor.SettlementCommand(
                from.getId(),
                goalAccount.getId(),
                new BigDecimal("650.0000"),
                initiator,
                "Saved towards " + goal.getName(),
                null,
                TransferType.INTERNAL,
                TransactionCategory.SAVINGS,
                null,
                0,
                java.util.List.of()));
        goal.refreshStatus();
    }

    /**
     * Money leaving the customer's account to the bank's own position.
     *
     * <p>The mirror image of {@link #fund}: a genuine double-entry movement, so the balance the
     * dashboard shows is the balance the ledger says.
     */
    private void spend(
            BankAccount account, User owner, String amount, String description, TransactionCategory category) {
        BankAccount systemAccount = accountService.getSystemAccount(account.getCurrency());
        transferExecutor.settle(new TransferExecutor.SettlementCommand(
                account.getId(),
                systemAccount.getId(),
                new BigDecimal(amount),
                owner,
                description,
                null,
                TransferType.WITHDRAWAL,
                category,
                null,
                0,
                java.util.List.of()));
    }

    /** Issues a demo card. The number is generated and encrypted by the same path the API uses. */
    private void issueCard(
            BankAccount account,
            User owner,
            CardType type,
            CardDesign design,
            CardNetwork network,
            String label) {
        cardRepository.save(Card.issue(
                account,
                owner,
                type,
                design,
                network,
                label,
                cardCrypto.protect(cardNumberGenerator.generate(network)),
                clock.instant()));
    }

    private User createUser(
            String username, String email, String password, String firstName, String lastName, UserRole role) {
        return userRepository.save(
                User.create(username, email, passwordEncoder.encode(password), firstName, lastName, role));
    }

    /** Funds an account from the bank's system account, producing real ledger entries. */
    private void fund(BankAccount account, BigDecimal amount, User initiator, String description) {
        fund(account, amount, initiator, description, TransactionCategory.TRANSFER);
    }

    private void fund(
            BankAccount account,
            BigDecimal amount,
            User initiator,
            String description,
            TransactionCategory category) {
        BankAccount systemAccount = accountService.getSystemAccount(account.getCurrency());
        transferExecutor.settle(new TransferExecutor.SettlementCommand(
                systemAccount.getId(),
                account.getId(),
                amount,
                initiator,
                description,
                null,
                TransferType.DEPOSIT,
                category,
                null,
                0,
                java.util.List.of()));
    }
}
