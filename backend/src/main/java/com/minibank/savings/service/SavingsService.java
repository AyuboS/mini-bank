package com.minibank.savings.service;

import com.minibank.account.domain.AccountPurpose;
import com.minibank.account.domain.BankAccount;
import com.minibank.account.service.AccountService;
import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.service.AuditEvent;
import com.minibank.audit.service.AuditService;
import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.error.NotFoundException;
import com.minibank.common.money.Money;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.notification.center.domain.NotificationSeverity;
import com.minibank.notification.center.domain.NotificationType;
import com.minibank.notification.center.service.NotificationService;
import com.minibank.savings.domain.SavingsGoal;
import com.minibank.savings.domain.SavingsGoalStatus;
import com.minibank.savings.dto.SavingsContributionRequest;
import com.minibank.savings.dto.SavingsGoalRequest;
import com.minibank.savings.repository.SavingsGoalRepository;
import com.minibank.transfer.domain.TransactionCategory;
import com.minibank.transfer.domain.Transfer;
import com.minibank.transfer.domain.TransferType;
import com.minibank.transfer.service.TransferExecutor;
import com.minibank.user.domain.User;
import com.minibank.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Savings goals: opening them, and moving money in and out.
 *
 * <h2>Why contributions go through the ledger</h2>
 *
 * <p>Every contribution and withdrawal is a real settled transfer between two of the customer's own
 * accounts, executed by {@link TransferExecutor}. That buys, for free, everything a hand-rolled
 * "savings balance" would have had to reinvent badly: atomicity, the row locks that stop a double
 * spend, double-entry bookkeeping, and an audit trail. A goal is a label over an account, not a
 * parallel accounting system.
 *
 * <p>The consequence worth stating plainly: money in a goal is not conjured and not hidden. It sits
 * in an account the customer owns, and every euro of it can be traced to the movement that put it
 * there.
 */
@Service
public class SavingsService {

    private static final Logger log = LoggerFactory.getLogger(SavingsService.class);

    /**
     * A cap on goals per customer.
     *
     * <p>Each goal opens a real bank account, so an unbounded count is an unbounded number of
     * accounts. Twelve is generous for the feature's purpose and small enough that the savings
     * screen stays readable.
     */
    private static final int MAX_GOALS_PER_USER = 12;

    private final SavingsGoalRepository goals;
    private final AccountService accountService;
    private final TransferExecutor transferExecutor;
    private final UserRepository users;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public SavingsService(
            SavingsGoalRepository goals,
            AccountService accountService,
            TransferExecutor transferExecutor,
            UserRepository users,
            AuditService auditService,
            NotificationService notificationService) {
        this.goals = goals;
        this.accountService = accountService;
        this.transferExecutor = transferExecutor;
        this.users = users;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<SavingsGoal> list(UUID ownerId) {
        return goals.findAllOwnedBy(ownerId);
    }

    @Transactional(readOnly = true)
    public SavingsGoal get(UUID goalId, AuthenticatedUser caller) {
        return requireOwnGoal(goalId, caller);
    }

    /** Opens a goal, and with it the account that will hold its money. */
    @Transactional
    public SavingsGoal create(SavingsGoalRequest request, AuthenticatedUser caller) {
        User owner = users.findById(caller.getId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));

        if (goals.countByOwnerIdAndStatusNot(owner.getId(), SavingsGoalStatus.ARCHIVED) >= MAX_GOALS_PER_USER) {
            throw new BusinessRuleException(
                    ErrorCode.SAVINGS_GOAL_LIMIT_REACHED,
                    "A customer may hold at most %d active goals".formatted(MAX_GOALS_PER_USER));
        }

        BigDecimal target = Money.requireValidAmount(request.targetAmount(), request.currency());
        BankAccount backing = accountService.openAccount(owner, request.currency(), AccountPurpose.SAVINGS);

        SavingsGoal goal = goals.save(SavingsGoal.open(
                owner,
                backing,
                request.name().trim(),
                target,
                request.deadline(),
                request.accent() == null ? "violet" : request.accent()));

        auditService.record(AuditEvent.success(AuditAction.SAVINGS_GOAL_CREATED)
                .by(owner)
                .on("SavingsGoal", goal.getId())
                .with("name", goal.getName())
                .with("target", target)
                .with("currency", request.currency()));
        log.info("Opened savings goal {} backed by account {}", goal.getId(), backing.getAccountNumber());
        return goal;
    }

    /** Renames a goal, moves its target or its deadline. The money is untouched. */
    @Transactional
    public SavingsGoal update(UUID goalId, SavingsGoalRequest request, AuthenticatedUser caller) {
        SavingsGoal goal = requireOwnGoal(goalId, caller);
        goal.setName(request.name().trim());
        goal.setTargetAmount(Money.requireValidAmount(request.targetAmount(), goal.getCurrency()));
        goal.setDeadline(request.deadline());
        if (request.accent() != null) {
            goal.setAccent(request.accent());
        }
        // The target may have moved past or below what is already saved.
        goal.refreshStatus();

        auditService.record(AuditEvent.success(AuditAction.SAVINGS_GOAL_UPDATED)
                .by(caller.getId(), caller.getUsername())
                .on("SavingsGoal", goalId));
        return goal;
    }

    /**
     * Moves money from a spending account into the goal.
     *
     * <p>Runs through settlement, so an insufficient balance is refused by the same code that
     * refuses any other payment, under the same lock.
     */
    @Transactional
    public SavingsGoal contribute(UUID goalId, SavingsContributionRequest request, AuthenticatedUser caller) {
        SavingsGoal goal = requireOwnGoal(goalId, caller);
        goal.requireContributable();

        BankAccount source = requireOwnSpendingAccount(request.accountId(), caller);
        requireSameCurrency(source, goal);

        BigDecimal amount = Money.requireValidAmount(request.amount(), goal.getCurrency());
        boolean wasAchieved = goal.isAchieved();

        move(source, goal.getAccount(), amount, caller, "Saved towards " + goal.getName());
        goal.refreshStatus();

        auditService.record(AuditEvent.success(AuditAction.SAVINGS_CONTRIBUTION)
                .by(caller.getId(), caller.getUsername())
                .on("SavingsGoal", goalId)
                .with("amount", amount));

        if (!wasAchieved && goal.isAchieved()) {
            notificationService.publish(
                    goal.getOwner(),
                    NotificationType.TRANSACTION,
                    NotificationSeverity.SUCCESS,
                    "Goal reached: " + goal.getName(),
                    "You have saved the full %s %s. The money stays yours to move whenever you want."
                            .formatted(goal.getCurrency().symbol(), goal.getTargetAmount().stripTrailingZeros()
                                    .toPlainString()),
                    "SavingsGoal",
                    goal.getId());
        }
        return goal;
    }

    /** Moves money back out of the goal into a spending account. */
    @Transactional
    public SavingsGoal withdraw(UUID goalId, SavingsContributionRequest request, AuthenticatedUser caller) {
        SavingsGoal goal = requireOwnGoal(goalId, caller);
        BankAccount destination = requireOwnSpendingAccount(request.accountId(), caller);
        requireSameCurrency(destination, goal);

        BigDecimal amount = Money.requireValidAmount(request.amount(), goal.getCurrency());

        move(goal.getAccount(), destination, amount, caller, "Withdrawn from " + goal.getName());
        // Taking money out of a completed goal makes it active again, rather than leaving a badge
        // claiming it is done.
        goal.refreshStatus();

        auditService.record(AuditEvent.success(AuditAction.SAVINGS_WITHDRAWAL)
                .by(caller.getId(), caller.getUsername())
                .on("SavingsGoal", goalId)
                .with("amount", amount));
        return goal;
    }

    /**
     * Archives a goal.
     *
     * <p>Refuses while money is still in it. Closing a goal is a filing action, and quietly leaving
     * a balance in an account the customer can no longer see would be a way to lose money.
     */
    @Transactional
    public void archive(UUID goalId, AuthenticatedUser caller) {
        SavingsGoal goal = requireOwnGoal(goalId, caller);
        if (goal.saved().signum() != 0) {
            throw new BusinessRuleException(ErrorCode.SAVINGS_GOAL_NOT_EMPTY);
        }
        goal.setStatus(SavingsGoalStatus.ARCHIVED);
        auditService.record(AuditEvent.success(AuditAction.SAVINGS_GOAL_ARCHIVED)
                .by(caller.getId(), caller.getUsername())
                .on("SavingsGoal", goalId));
    }

    /** One settled internal transfer, categorised as savings so the analytics never call it spending. */
    private Transfer move(
            BankAccount from, BankAccount to, BigDecimal amount, AuthenticatedUser caller, String description) {
        User initiator = users.findById(caller.getId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
        return transferExecutor.settle(new TransferExecutor.SettlementCommand(
                from.getId(),
                to.getId(),
                amount,
                initiator,
                description,
                null,
                TransferType.INTERNAL,
                TransactionCategory.SAVINGS,
                null,
                0,
                List.of()));
    }

    private SavingsGoal requireOwnGoal(UUID goalId, AuthenticatedUser caller) {
        SavingsGoal goal = goals.findWithAccountById(goalId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SAVINGS_GOAL_NOT_FOUND));
        // Somebody else's goal is reported as missing rather than forbidden, so the endpoint cannot
        // be used to discover which goal ids exist.
        if (!goal.isOwnedBy(caller.getId())) {
            log.warn("User {} attempted to access savings goal {} they do not own", caller.getId(), goalId);
            throw new NotFoundException(ErrorCode.SAVINGS_GOAL_NOT_FOUND);
        }
        return goal;
    }

    /**
     * The account funding a contribution must be the customer's own, and must be a spending account.
     *
     * <p>Blocking savings-to-savings movement is deliberate: it would let money hop between goals
     * without ever appearing on a spending account, which makes the analytics harder to reason about
     * for no benefit the customer actually asked for.
     */
    private BankAccount requireOwnSpendingAccount(UUID accountId, AuthenticatedUser caller) {
        BankAccount account = accountService.getAccessibleAccount(accountId, caller);
        if (!account.isOwnedBy(caller.getId())) {
            throw new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        if (account.isSavingsAccount()) {
            throw new BusinessRuleException(
                    ErrorCode.ACCOUNT_NOT_ACTIVE, "Money must move through one of your spending accounts");
        }
        return account;
    }

    private static void requireSameCurrency(BankAccount account, SavingsGoal goal) {
        if (account.getCurrency() != goal.getCurrency()) {
            throw new BusinessRuleException(
                    ErrorCode.CURRENCY_MISMATCH,
                    "This goal saves in %s; use a %s account".formatted(goal.getCurrency(), goal.getCurrency()));
        }
    }
}
