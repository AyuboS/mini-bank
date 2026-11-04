package com.minibank.savings.domain;

import com.minibank.account.domain.BankAccount;
import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.money.CurrencyCode;
import com.minibank.common.persistence.AuditedEntity;
import com.minibank.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A named savings target, backed by a real account.
 *
 * <p>There is no {@code currentAmount} field, and that is the design. Progress is {@link
 * #saved()} — the balance of the backing account — so the figure on the savings screen is the same
 * number the ledger produces, by construction rather than by a job that keeps two totals in step.
 * A stored total would eventually disagree with the ledger after a reversal, a failed contribution
 * or a concurrent write, and a savings page that disagrees with the ledger is worse than none.
 */
@Entity
@Table(name = "savings_goals")
@Getter
@Setter
public class SavingsGoal extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User owner;

    /** The account holding the money. One goal, one account, enforced by a unique constraint. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private BankAccount account;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "target_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal targetAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private CurrencyCode currency;

    /** Optional. Null means "no particular date", which is a legitimate way to save. */
    @Column(name = "deadline")
    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SavingsGoalStatus status = SavingsGoalStatus.ACTIVE;

    /** Which of the wallet's accent colours the goal is drawn in. Presentation only. */
    @Column(name = "accent", nullable = false, length = 20)
    private String accent = "violet";

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected SavingsGoal() {
        // for JPA
    }

    public static SavingsGoal open(
            User owner,
            BankAccount account,
            String name,
            BigDecimal targetAmount,
            LocalDate deadline,
            String accent) {
        SavingsGoal goal = new SavingsGoal();
        goal.owner = owner;
        goal.account = account;
        goal.name = name;
        goal.targetAmount = targetAmount;
        goal.currency = account.getCurrency();
        goal.deadline = deadline;
        goal.accent = accent;
        goal.status = SavingsGoalStatus.ACTIVE;
        return goal;
    }

    /** How much has been put aside: the backing account's balance, and nothing else. */
    public BigDecimal saved() {
        return account.getBalance();
    }

    public BigDecimal remaining() {
        BigDecimal remaining = targetAmount.subtract(saved());
        return remaining.signum() < 0 ? BigDecimal.ZERO.setScale(remaining.scale()) : remaining;
    }

    /**
     * Progress as a percentage, 0–100, rounded down.
     *
     * <p>Rounded down so a goal never claims 100% until it genuinely is complete. Seeing "100%" next
     * to €1,499.60 of a €1,500 target is the kind of small lie that makes a customer stop believing
     * the rest of the screen.
     */
    public int progressPercent() {
        if (targetAmount.signum() <= 0) {
            return 0;
        }
        BigDecimal ratio = saved().divide(targetAmount, 4, RoundingMode.DOWN);
        int percent = ratio.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).intValue();
        return Math.min(100, Math.max(0, percent));
    }

    public boolean isAchieved() {
        return saved().compareTo(targetAmount) >= 0;
    }

    /**
     * Recomputes the status from the balance.
     *
     * <p>Called after every contribution and withdrawal so the badge follows the money in both
     * directions: taking money back out of a completed goal returns it to ACTIVE rather than
     * leaving it permanently marked done.
     */
    public void refreshStatus() {
        if (status == SavingsGoalStatus.ARCHIVED) {
            return;
        }
        status = isAchieved() ? SavingsGoalStatus.ACHIEVED : SavingsGoalStatus.ACTIVE;
    }

    public boolean isOwnedBy(UUID userId) {
        return owner != null && owner.getId().equals(userId);
    }

    /** @throws BusinessRuleException if the goal has been archived and should not be paid into */
    public void requireContributable() {
        if (status == SavingsGoalStatus.ARCHIVED) {
            throw new BusinessRuleException(
                    ErrorCode.SAVINGS_GOAL_ARCHIVED, "This goal is archived; restore it before adding money");
        }
    }
}
