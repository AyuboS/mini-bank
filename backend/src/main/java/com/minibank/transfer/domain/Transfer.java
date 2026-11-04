package com.minibank.transfer.domain;

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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The business record of a money movement.
 *
 * <p>Status changes go through {@link #transitionTo}, which rejects illegal moves. Allowing callers
 * to assign the field directly is how a settled transfer ends up back in PENDING, or how a failed
 * one is quietly marked complete.
 */
@Entity
@Table(name = "transfers")
@Getter
@Setter
public class Transfer extends AuditedEntity {

    @Column(name = "reference", nullable = false, length = 32, updatable = false)
    private String reference;

    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_type", nullable = false, length = 20, updatable = false)
    private TransferType transferType = TransferType.INTERNAL;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_account_id", nullable = false, updatable = false)
    private BankAccount sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_account_id", nullable = false, updatable = false)
    private BankAccount destinationAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by_user_id", updatable = false)
    private User initiatedBy;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private CurrencyCode currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransferStatus status = TransferStatus.PENDING;

    @Column(name = "description", length = 140)
    private String description;

    /**
     * What the payment was for. Null means uncategorised, which is a real state rather than a
     * missing value: the categoriser declines to guess when it is not confident, and the customer
     * can see the blank and correct it.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20)
    private TransactionCategory category;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "risk_decision", length = 10)
    private String riskDecision;

    @Column(name = "risk_score")
    private Integer riskScore;

    /** Why the risk engine reached its verdict, so a held transfer can be reviewed on its own. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_reasons", columnDefinition = "jsonb")
    private List<String> riskReasons;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_transfer_id", updatable = false)
    private Transfer reversalOf;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Transfer() {
        // for JPA
    }

    public static Transfer initiate(
            String reference,
            TransferType type,
            BankAccount source,
            BankAccount destination,
            BigDecimal amount,
            User initiatedBy,
            String description,
            String idempotencyKey) {
        Transfer transfer = new Transfer();
        transfer.reference = reference;
        transfer.transferType = type;
        transfer.sourceAccount = source;
        transfer.destinationAccount = destination;
        transfer.amount = amount;
        transfer.currency = source.getCurrency();
        transfer.initiatedBy = initiatedBy;
        transfer.description = description;
        transfer.idempotencyKey = idempotencyKey;
        transfer.status = TransferStatus.PENDING;
        return transfer;
    }

    /**
     * Moves the transfer to a new status.
     *
     * @throws BusinessRuleException if the transition is not permitted by the state machine
     */
    public void transitionTo(TransferStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessRuleException(
                    ErrorCode.INVALID_TRANSFER_STATE,
                    "Transfer %s cannot move from %s to %s".formatted(reference, status, target));
        }
        status = target;
        if (target.isSettled() && completedAt == null) {
            completedAt = now;
        }
    }

    /** Marks the transfer as failed, recording why. */
    public void fail(String reason, Instant now) {
        transitionTo(TransferStatus.FAILED, now);
        this.failureReason = truncateReason(reason);
    }

    public void applyRiskOutcome(String decision, int score, List<String> reasons) {
        this.riskDecision = decision;
        this.riskScore = score;
        this.riskReasons = reasons == null || reasons.isEmpty() ? null : List.copyOf(reasons);
    }

    public boolean involvesAccount(java.util.UUID accountId) {
        return sourceAccount.getId().equals(accountId) || destinationAccount.getId().equals(accountId);
    }

    private static String truncateReason(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= 255 ? reason : reason.substring(0, 255);
    }
}
