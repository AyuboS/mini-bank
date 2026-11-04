package com.minibank.scheduling.domain;

import com.minibank.account.domain.BankAccount;
import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.money.CurrencyCode;
import com.minibank.common.persistence.AuditedEntity;
import com.minibank.transfer.domain.TransactionCategory;
import com.minibank.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A transfer that runs later, and possibly again: a standing order.
 *
 * <p>Covers both "send this on the 1st of September" and "send this every month", because the two
 * differ only in {@link ScheduleFrequency}. Splitting them would duplicate the executor, the
 * pause/resume rules and the due-date arithmetic for no gain.
 *
 * <p>Nothing here executes anything. The entity records intent and tracks outcome; the money is
 * moved by the ordinary transfer path, so a due run is subject to the same limits, risk checks and
 * ledger guarantees as a payment somebody typed by hand.
 */
@Entity
@Table(name = "scheduled_transfers")
@Getter
@Setter
public class ScheduledTransfer extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_account_id", nullable = false, updatable = false)
    private BankAccount sourceAccount;

    @Column(name = "destination_account_number", nullable = false, length = 34, updatable = false)
    private String destinationAccountNumber;

    /** The payee's name as it was at set-up. Kept so the list does not have to resolve it each time. */
    @Column(name = "payee_name", length = 120)
    private String payeeName;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private CurrencyCode currency;

    @Column(name = "description", length = 140)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20)
    private TransactionCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private ScheduleFrequency frequency = ScheduleFrequency.ONCE;

    @Column(name = "next_run_on", nullable = false)
    private LocalDate nextRunOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ScheduleStatus status = ScheduleStatus.SCHEDULED;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    /** Why the most recent attempt did not go through. Cleared on the next success. */
    @Column(name = "last_failure_reason", length = 255)
    private String lastFailureReason;

    @Column(name = "runs_completed", nullable = false)
    private int runsCompleted;

    /** Optional stop date. Null means "until cancelled". */
    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ScheduledTransfer() {
        // for JPA
    }

    public static ScheduledTransfer create(
            User owner,
            BankAccount source,
            String destinationAccountNumber,
            String payeeName,
            BigDecimal amount,
            String description,
            TransactionCategory category,
            ScheduleFrequency frequency,
            LocalDate firstRun,
            LocalDate endsOn) {
        ScheduledTransfer order = new ScheduledTransfer();
        order.owner = owner;
        order.sourceAccount = source;
        order.destinationAccountNumber = destinationAccountNumber;
        order.payeeName = payeeName;
        order.amount = amount;
        order.currency = source.getCurrency();
        order.description = description;
        order.category = category;
        order.frequency = frequency;
        order.nextRunOn = firstRun;
        order.endsOn = endsOn;
        order.status = ScheduleStatus.SCHEDULED;
        return order;
    }

    public boolean isOwnedBy(UUID userId) {
        return owner != null && owner.getId().equals(userId);
    }

    public boolean isDue(LocalDate today) {
        return status == ScheduleStatus.SCHEDULED && !nextRunOn.isAfter(today);
    }

    /**
     * Records a run that moved money, and advances to the next date.
     *
     * <p>Advancing is unconditional on success: a monthly order that has just paid must not be able
     * to pay again on the same date, whatever the runner does next.
     */
    public void recordSuccess(Instant now) {
        lastRunAt = now;
        lastFailureReason = null;
        runsCompleted++;
        advance();
    }

    /**
     * Records a run that did not move money, and advances anyway.
     *
     * <p>Advancing after a failure is the deliberate choice. A standing order that could not be paid
     * on the 1st because the account was empty should try again next month, not retry every hour for
     * a month and fill the customer's feed with the same refusal. The reason is kept so the customer
     * can see what happened, and the next due date is when they will next be asked for the money.
     */
    public void recordFailure(String reason, Instant now) {
        lastRunAt = now;
        lastFailureReason = reason == null || reason.length() <= 255 ? reason : reason.substring(0, 255);
        advance();
    }

    private void advance() {
        LocalDate next = frequency.next(nextRunOn);
        if (next == null || (endsOn != null && next.isAfter(endsOn))) {
            status = ScheduleStatus.COMPLETED;
            return;
        }
        nextRunOn = next;
    }

    public void pause() {
        requireModifiable();
        status = ScheduleStatus.PAUSED;
    }

    /**
     * Resumes a paused order.
     *
     * @param today used to move a next-run date that fell into the past while the order was paused.
     *     Resuming must not fire an immediate catch-up run for every date that was missed; the
     *     customer paused it precisely so those payments would not happen.
     */
    public void resume(LocalDate today) {
        requireModifiable();
        status = ScheduleStatus.SCHEDULED;
        if (nextRunOn.isBefore(today)) {
            if (!frequency.repeats()) {
                nextRunOn = today;
                return;
            }
            LocalDate candidate = nextRunOn;
            while (candidate.isBefore(today)) {
                candidate = frequency.next(candidate);
            }
            nextRunOn = candidate;
            if (endsOn != null && nextRunOn.isAfter(endsOn)) {
                status = ScheduleStatus.COMPLETED;
            }
        }
    }

    public void cancel() {
        requireModifiable();
        status = ScheduleStatus.CANCELLED;
    }

    /** @throws BusinessRuleException if the order has already finished or been cancelled */
    public void requireModifiable() {
        if (!status.isLive()) {
            throw new BusinessRuleException(ErrorCode.SCHEDULED_TRANSFER_NOT_MODIFIABLE);
        }
    }
}
