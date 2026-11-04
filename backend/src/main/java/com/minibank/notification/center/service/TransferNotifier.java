package com.minibank.notification.center.service;

import com.minibank.account.domain.BankAccount;
import com.minibank.budget.domain.Budget;
import com.minibank.budget.repository.BudgetRepository;
import com.minibank.common.money.Money;
import com.minibank.notification.center.domain.NotificationSeverity;
import com.minibank.notification.center.domain.NotificationType;
import com.minibank.transfer.domain.Transfer;
import com.minibank.transfer.repository.TransferRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a settled transfer into the notifications both sides should see.
 *
 * <p>Kept out of {@code TransferService} so that the rules about what a customer is told live in one
 * place and can change without touching the code that moves money. Everything here is best-effort by
 * construction — see {@link NotificationService} — so a notification failure cannot cost somebody
 * their payment.
 */
@Component
public class TransferNotifier {

    private static final Logger log = LoggerFactory.getLogger(TransferNotifier.class);

    /** The point at which a budget is worth mentioning. Below this the customer does not want to hear. */
    private static final int BUDGET_WARNING_PERCENT = 80;

    private final NotificationService notifications;
    private final BudgetRepository budgets;
    private final TransferRepository transfers;

    public TransferNotifier(
            NotificationService notifications, BudgetRepository budgets, TransferRepository transfers) {
        this.notifications = notifications;
        this.budgets = budgets;
        this.transfers = transfers;
    }

    /**
     * Tells the payer their money left, and the payee that money arrived.
     *
     * <p>Both sides are notified because both sides' balances just changed. Notifying only the payer
     * would leave a customer discovering an incoming payment by chance.
     *
     * <p>Takes an id, not an entity. Settlement has already committed by the time this runs, so the
     * {@code Transfer} the caller holds belongs to a closed persistence context and reading its lazy
     * owner associations would throw. Re-reading it here, inside this transaction, is what makes the
     * account owners available at all.
     */
    @Transactional
    public void settled(UUID transferId, BigDecimal spentInCategoryThisMonth) {
        Transfer transfer = transfers.findWithAccountsById(transferId).orElse(null);
        if (transfer == null) {
            log.warn("Transfer {} vanished before its notifications could be written", transferId);
            return;
        }
        BankAccount source = transfer.getSourceAccount();
        BankAccount destination = transfer.getDestinationAccount();
        String symbol = transfer.getCurrency().symbol();
        String amount = plain(transfer.getAmount());

        if (source.getOwner() != null) {
            notifications.publish(
                    source.getOwner(),
                    NotificationType.TRANSACTION,
                    NotificationSeverity.INFO,
                    "%s%s sent".formatted(symbol, amount),
                    "%s · to %s"
                            .formatted(
                                    describe(transfer),
                                    mask(destination.getAccountNumber())),
                    "Transfer",
                    transfer.getId());
        }

        if (destination.getOwner() != null && !destination.getOwner().equals(source.getOwner())) {
            notifications.publish(
                    destination.getOwner(),
                    NotificationType.TRANSACTION,
                    NotificationSeverity.SUCCESS,
                    "%s%s received".formatted(symbol, amount),
                    "%s · from %s".formatted(describe(transfer), mask(source.getAccountNumber())),
                    "Transfer",
                    transfer.getId());
        }

        if (spentInCategoryThisMonth != null) {
            warnIfBudgetTight(transfer, spentInCategoryThisMonth);
        }
    }

    /**
     * Warns once the category's budget is close to, or past, its cap.
     *
     * <p>Only fires when the customer set a budget for the category. An unprompted "you have spent
     * a lot on groceries" is an opinion; a budget the customer chose is a promise they asked to be
     * held to.
     */
    private void warnIfBudgetTight(Transfer transfer, BigDecimal spent) {
        if (transfer.getCategory() == null || transfer.getSourceAccount().getOwner() == null) {
            return;
        }
        Optional<Budget> budget = budgets.findByOwnerIdAndCategoryAndCurrency(
                transfer.getSourceAccount().getOwner().getId(), transfer.getCategory(), transfer.getCurrency());
        if (budget.isEmpty()) {
            return;
        }

        BigDecimal cap = budget.get().getAmount();
        if (cap.signum() <= 0) {
            return;
        }
        int used = spent.multiply(BigDecimal.valueOf(100)).divide(cap, 0, RoundingMode.HALF_UP).intValue();
        if (used < BUDGET_WARNING_PERCENT) {
            return;
        }

        boolean over = used > 100;
        String symbol = transfer.getCurrency().symbol();
        notifications.publish(
                transfer.getSourceAccount().getOwner(),
                NotificationType.BUDGET,
                over ? NotificationSeverity.WARNING : NotificationSeverity.INFO,
                over
                        ? "%s budget passed".formatted(transfer.getCategory().displayName())
                        : "%d%% of your %s budget used"
                                .formatted(used, transfer.getCategory().displayName()),
                "%s%s of %s%s this month".formatted(symbol, plain(spent), symbol, plain(cap)),
                "Budget",
                budget.get().getId());
        log.debug("Budget notice sent for category {} at {}%", transfer.getCategory(), used);
    }

    /** The customer's own words if they wrote any, otherwise the category, otherwise nothing useful. */
    private static String describe(Transfer transfer) {
        if (transfer.getDescription() != null && !transfer.getDescription().isBlank()) {
            return transfer.getDescription();
        }
        return transfer.getCategory() == null ? "Transfer" : transfer.getCategory().displayName();
    }

    private static String mask(String accountNumber) {
        return accountNumber == null || accountNumber.length() < 4
                ? "an account"
                : "••••" + accountNumber.substring(accountNumber.length() - 4);
    }

    private static String plain(BigDecimal amount) {
        return (amount == null ? Money.zero() : amount).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
