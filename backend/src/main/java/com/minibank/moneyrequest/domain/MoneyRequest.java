package com.minibank.moneyrequest.domain;

import com.minibank.account.domain.BankAccount;
import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.money.CurrencyCode;
import com.minibank.common.persistence.AuditedEntity;
import com.minibank.transfer.domain.Transfer;
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
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * An invitation to pay.
 *
 * <p>The request moves no money and cannot. Only the payer can settle it, and settling is an
 * ordinary transfer that the payer initiates; this row merely records which transfer did so. The
 * alternative design — a request that pulls funds — would amount to letting one customer debit
 * another, which is not something a confirmation dialog can make acceptable.
 */
@Entity
@Table(name = "money_requests")
@Getter
@Setter
public class MoneyRequest extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_user_id", nullable = false, updatable = false)
    private User requester;

    /** Where the money lands if the request is paid. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_account_id", nullable = false, updatable = false)
    private BankAccount requesterAccount;

    @Column(name = "payer_account_number", nullable = false, length = 34, updatable = false)
    private String payerAccountNumber;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private CurrencyCode currency;

    @Column(name = "description", length = 140, updatable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MoneyRequestStatus status = MoneyRequestStatus.PENDING;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /**
     * The transfer that settled this request.
     *
     * <p>A database constraint ties this to the PAID status in both directions, so no code path can
     * mark a request settled without producing the movement that settled it.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settled_transfer_id")
    private Transfer settledTransfer;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected MoneyRequest() {
        // for JPA
    }

    public static MoneyRequest open(
            User requester,
            BankAccount requesterAccount,
            String payerAccountNumber,
            BigDecimal amount,
            String description,
            Instant expiresAt) {
        MoneyRequest request = new MoneyRequest();
        request.requester = requester;
        request.requesterAccount = requesterAccount;
        request.payerAccountNumber = payerAccountNumber;
        request.amount = amount;
        request.currency = requesterAccount.getCurrency();
        request.description = description;
        request.expiresAt = expiresAt;
        request.status = MoneyRequestStatus.PENDING;
        return request;
    }

    /**
     * True once the expiry has passed.
     *
     * <p>Derived from the clock rather than written by a job. A job that fails to run would leave
     * expired requests looking payable, and this is the direction that must not fail.
     */
    public boolean hasExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    /** The status as it should be reported, taking the calendar into account. */
    public MoneyRequestStatus effectiveStatus(Instant now) {
        if (status == MoneyRequestStatus.PENDING && hasExpired(now)) {
            return MoneyRequestStatus.EXPIRED;
        }
        return status;
    }

    public void markPaid(Transfer transfer, Instant now) {
        requireActionable(now);
        status = MoneyRequestStatus.PAID;
        settledTransfer = transfer;
        settledAt = now;
    }

    public void decline(Instant now) {
        requireActionable(now);
        status = MoneyRequestStatus.DECLINED;
        settledAt = now;
    }

    public void cancel(Instant now) {
        requireActionable(now);
        status = MoneyRequestStatus.CANCELLED;
        settledAt = now;
    }

    /**
     * Guards any action that changes the request's outcome.
     *
     * @throws BusinessRuleException if it has already been settled, or has expired
     */
    public void requireActionable(Instant now) {
        if (status.isSettled()) {
            throw new BusinessRuleException(ErrorCode.MONEY_REQUEST_NOT_PENDING);
        }
        if (hasExpired(now)) {
            throw new BusinessRuleException(ErrorCode.MONEY_REQUEST_EXPIRED);
        }
    }

    public boolean wasRaisedBy(UUID userId) {
        return requester != null && requester.getId().equals(userId);
    }
}
