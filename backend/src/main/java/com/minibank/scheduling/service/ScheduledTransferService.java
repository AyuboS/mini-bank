package com.minibank.scheduling.service;

import com.minibank.account.domain.BankAccount;
import com.minibank.account.service.AccountService;
import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.service.AuditEvent;
import com.minibank.audit.service.AuditService;
import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.error.ForbiddenException;
import com.minibank.common.error.NotFoundException;
import com.minibank.common.money.Money;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.scheduling.domain.ScheduleStatus;
import com.minibank.scheduling.domain.ScheduledTransfer;
import com.minibank.scheduling.dto.ScheduledTransferRequest;
import com.minibank.scheduling.repository.ScheduledTransferRepository;
import com.minibank.transfer.service.TransactionCategorizer;
import com.minibank.user.domain.User;
import com.minibank.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Setting up and managing standing orders.
 *
 * <p>Execution lives in {@link ScheduledTransferRunner}. Keeping the two apart matters: this class
 * runs inside a customer's request and answers to them, while the runner runs unattended and must
 * never let one customer's failed payment stop another's from being attempted.
 */
@Service
public class ScheduledTransferService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTransferService.class);

    /**
     * A ceiling on live orders per customer.
     *
     * <p>Each live order is work the runner does every time it fires. The cap keeps one account from
     * turning into an unbounded batch job, and forty is far beyond what the feature is for.
     */
    private static final int MAX_LIVE_ORDERS = 40;

    private final ScheduledTransferRepository orders;
    private final AccountService accountService;
    private final UserRepository users;
    private final TransactionCategorizer categorizer;
    private final AuditService auditService;
    private final Clock clock;

    public ScheduledTransferService(
            ScheduledTransferRepository orders,
            AccountService accountService,
            UserRepository users,
            TransactionCategorizer categorizer,
            AuditService auditService,
            Clock clock) {
        this.orders = orders;
        this.accountService = accountService;
        this.users = users;
        this.categorizer = categorizer;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ScheduledTransfer> list(UUID ownerId) {
        return orders.findAllOwnedBy(ownerId);
    }

    @Transactional(readOnly = true)
    public ScheduledTransfer get(UUID id, AuthenticatedUser caller) {
        return requireOwnOrder(id, caller);
    }

    @Transactional
    public ScheduledTransfer create(ScheduledTransferRequest request, AuthenticatedUser caller) {
        User owner = users.findById(caller.getId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));

        if (orders.countByOwnerIdAndStatus(owner.getId(), ScheduleStatus.SCHEDULED) >= MAX_LIVE_ORDERS) {
            throw new BusinessRuleException(
                    ErrorCode.CONFLICT, "You already have the maximum number of scheduled transfers");
        }

        BankAccount source = accountService.getAccessibleAccount(request.fromAccountId(), caller);
        if (!source.isOwnedBy(caller.getId())) {
            throw new ForbiddenException("Transfers may only be scheduled from your own accounts");
        }
        if (source.isSystemAccount()) {
            throw new ForbiddenException("System accounts cannot be used as a transfer source");
        }

        // Resolved now so the order fails at set-up rather than silently at 03:00 on the 1st.
        BankAccount destination = accountService.getByAccountNumber(request.toAccountNumber());
        if (destination.getId().equals(source.getId())) {
            throw new BusinessRuleException(ErrorCode.SAME_ACCOUNT_TRANSFER);
        }
        if (destination.getCurrency() != source.getCurrency()) {
            throw new BusinessRuleException(
                    ErrorCode.CURRENCY_MISMATCH,
                    "Cannot schedule a transfer between a %s account and a %s account"
                            .formatted(source.getCurrency(), destination.getCurrency()));
        }

        LocalDate today = today();
        if (request.startOn().isBefore(today)) {
            throw new BusinessRuleException(ErrorCode.SCHEDULED_DATE_IN_PAST);
        }
        if (request.endsOn() != null && request.endsOn().isBefore(request.startOn())) {
            throw new BusinessRuleException(
                    ErrorCode.VALIDATION_ERROR, "The end date must not be before the first run");
        }

        BigDecimal amount = Money.requireValidAmount(request.amount(), source.getCurrency());

        ScheduledTransfer order = orders.save(ScheduledTransfer.create(
                owner,
                source,
                destination.getAccountNumber(),
                holderNameOf(destination),
                amount,
                request.description(),
                categorizer
                        .categorize(
                                request.category(),
                                request.description(),
                                com.minibank.transfer.domain.TransferType.INTERNAL)
                        .orElse(null),
                request.frequency(),
                request.startOn(),
                request.endsOn()));

        auditService.record(AuditEvent.success(AuditAction.SCHEDULED_TRANSFER_CREATED)
                .by(owner)
                .on("ScheduledTransfer", order.getId())
                .with("amount", amount)
                .with("frequency", request.frequency())
                .with("startOn", request.startOn()));
        log.info("Standing order {} created, first run {}", order.getId(), request.startOn());
        return order;
    }

    /**
     * Changes the amount, the description or the schedule of a live order.
     *
     * <p>The source and destination are not changeable. Repointing a standing order at a different
     * payee is a new arrangement, and letting it be an edit would make the audit trail read as
     * though the customer had always been paying the new one.
     */
    @Transactional
    public ScheduledTransfer update(UUID id, ScheduledTransferRequest request, AuthenticatedUser caller) {
        ScheduledTransfer order = requireOwnOrder(id, caller);
        order.requireModifiable();

        LocalDate today = today();
        if (request.startOn().isBefore(today)) {
            throw new BusinessRuleException(ErrorCode.SCHEDULED_DATE_IN_PAST);
        }

        order.setAmount(Money.requireValidAmount(request.amount(), order.getCurrency()));
        order.setDescription(request.description());
        order.setCategory(request.category());
        order.setFrequency(request.frequency());
        order.setNextRunOn(request.startOn());
        order.setEndsOn(request.endsOn());

        auditService.record(AuditEvent.success(AuditAction.SCHEDULED_TRANSFER_UPDATED)
                .by(caller.getId(), caller.getUsername())
                .on("ScheduledTransfer", id));
        return order;
    }

    @Transactional
    public ScheduledTransfer pause(UUID id, AuthenticatedUser caller) {
        ScheduledTransfer order = requireOwnOrder(id, caller);
        order.pause();
        auditService.record(AuditEvent.success(AuditAction.SCHEDULED_TRANSFER_PAUSED)
                .by(caller.getId(), caller.getUsername())
                .on("ScheduledTransfer", id));
        return order;
    }

    @Transactional
    public ScheduledTransfer resume(UUID id, AuthenticatedUser caller) {
        ScheduledTransfer order = requireOwnOrder(id, caller);
        order.resume(today());
        auditService.record(AuditEvent.success(AuditAction.SCHEDULED_TRANSFER_RESUMED)
                .by(caller.getId(), caller.getUsername())
                .on("ScheduledTransfer", id)
                .with("nextRunOn", order.getNextRunOn()));
        return order;
    }

    @Transactional
    public ScheduledTransfer cancel(UUID id, AuthenticatedUser caller) {
        ScheduledTransfer order = requireOwnOrder(id, caller);
        order.cancel();
        auditService.record(AuditEvent.success(AuditAction.SCHEDULED_TRANSFER_CANCELLED)
                .by(caller.getId(), caller.getUsername())
                .on("ScheduledTransfer", id));
        return order;
    }

    private ScheduledTransfer requireOwnOrder(UUID id, AuthenticatedUser caller) {
        ScheduledTransfer order = orders.findWithAccountById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SCHEDULED_TRANSFER_NOT_FOUND));
        if (!order.isOwnedBy(caller.getId())) {
            log.warn("User {} attempted to access standing order {} they do not own", caller.getId(), id);
            throw new NotFoundException(ErrorCode.SCHEDULED_TRANSFER_NOT_FOUND);
        }
        return order;
    }

    /**
     * The payee's name, or null.
     *
     * <p>Null for a system account, which has no holder. Nothing more than the name is copied: an
     * account number the customer already typed is not a secret, but the payee's other details are
     * not the payer's to keep.
     */
    private static String holderNameOf(BankAccount account) {
        return account.getOwner() == null ? null : account.getOwner().fullName();
    }

    /**
     * Today, in UTC.
     *
     * <p>The runner and this service must agree on what "today" means or an order created for today
     * could be judged to be in the past. UTC is the one clock both sides share.
     */
    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
