package com.minibank.scheduling.service;

import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.service.AuditEvent;
import com.minibank.audit.service.AuditService;
import com.minibank.notification.center.domain.NotificationSeverity;
import com.minibank.notification.center.domain.NotificationType;
import com.minibank.notification.center.service.NotificationService;
import com.minibank.scheduling.domain.ScheduledTransfer;
import com.minibank.scheduling.repository.ScheduledTransferRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The database side of running a standing order, kept deliberately apart from the attempt itself.
 *
 * <h2>Why this is a separate bean</h2>
 *
 * <p>It is not organisational tidiness — it is a correctness requirement, and the bug it fixes is
 * worth stating plainly.
 *
 * <p>Settlement is {@code @Transactional(REQUIRED)}. If the runner held an open transaction while
 * calling it, a refused payment — insufficient funds, a frozen account — would mark <em>that</em>
 * transaction rollback-only. The runner would then catch the exception, record the failure, advance
 * the due date, and lose all of it at commit time with an {@code UnexpectedRollback}. The order's
 * date would never move, so it would be retried on every tick, forever, notifying the customer of
 * the same refusal each time. Precisely the behaviour the design says must not happen.
 *
 * <p>So the runner holds no transaction across the attempt. It reads what it needs, attempts the
 * payment with nothing open, and then calls back into this bean, whose short {@code REQUIRES_NEW}
 * transactions are unaffected by whatever the attempt did.
 */
@Component
public class ScheduledTransferBookkeeper {

    private final ScheduledTransferRepository orders;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final Clock clock;

    public ScheduledTransferBookkeeper(
            ScheduledTransferRepository orders,
            AuditService auditService,
            NotificationService notificationService,
            Clock clock) {
        this.orders = orders;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    /**
     * Loads an order that is genuinely due, flattened into a value the caller can hold safely.
     *
     * <p>A detached record rather than the entity, because the caller uses it outside any
     * transaction and every association on the entity is lazy.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<DueOrder> loadDue(UUID orderId, LocalDate today) {
        return orders.findWithAccountById(orderId)
                // Re-checked here: the customer may have paused or cancelled it between the due
                // query and now.
                .filter(order -> order.isDue(today))
                .map(order -> new DueOrder(
                        order.getId(),
                        order.getOwner().getId(),
                        order.getOwner().getUsername(),
                        order.getSourceAccount().getId(),
                        order.getDestinationAccountNumber(),
                        order.getPayeeName(),
                        order.getAmount(),
                        order.getCurrency(),
                        order.getDescription(),
                        order.getCategory(),
                        order.getNextRunOn()));
    }

    /** Records a run that moved money, advances the schedule, and tells the customer. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(UUID orderId, LocalDate dueOn, UUID transferId, String reference) {
        ScheduledTransfer order = orders.findWithAccountById(orderId).orElse(null);
        if (order == null) {
            return;
        }
        Instant now = clock.instant();
        order.recordSuccess(now);

        auditService.record(AuditEvent.success(AuditAction.SCHEDULED_TRANSFER_EXECUTED)
                .by(order.getOwner())
                .on("ScheduledTransfer", orderId)
                .with("reference", reference)
                .with("amount", order.getAmount())
                .with("dueOn", dueOn));

        notificationService.publish(
                order.getOwner(),
                NotificationType.TRANSACTION,
                NotificationSeverity.SUCCESS,
                "Scheduled payment sent",
                "%s%s to %s. %s"
                        .formatted(
                                order.getCurrency().symbol(),
                                order.getAmount().stripTrailingZeros().toPlainString(),
                                payeeLabel(order),
                                nextRunNote(order)),
                "Transfer",
                transferId);
    }

    /**
     * Records a run that did not move money, and advances the schedule anyway.
     *
     * <p>Advancing after a refusal is the deliberate choice: a payment that could not be made on the
     * 1st should be attempted again next month, not retried every ten minutes for a month.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID orderId, LocalDate dueOn, String errorCode, String message) {
        ScheduledTransfer order = orders.findWithAccountById(orderId).orElse(null);
        if (order == null) {
            return;
        }
        order.recordFailure(message, clock.instant());

        auditService.record(AuditEvent.failure(AuditAction.SCHEDULED_TRANSFER_EXECUTION_FAILED)
                .by(order.getOwner())
                .on("ScheduledTransfer", orderId)
                .with("reason", errorCode)
                .with("dueOn", dueOn));

        notificationService.publish(
                order.getOwner(),
                NotificationType.TRANSACTION,
                NotificationSeverity.WARNING,
                "Scheduled payment did not go through",
                "%s%s to %s could not be sent: %s. %s"
                        .formatted(
                                order.getCurrency().symbol(),
                                order.getAmount().stripTrailingZeros().toPlainString(),
                                payeeLabel(order),
                                message,
                                nextRunNote(order)),
                "ScheduledTransfer",
                orderId);
    }

    private static String payeeLabel(ScheduledTransfer order) {
        return order.getPayeeName() == null ? order.getDestinationAccountNumber() : order.getPayeeName();
    }

    private static String nextRunNote(ScheduledTransfer order) {
        return order.getStatus().isLive()
                ? "Next payment " + order.getNextRunOn() + "."
                : "This was the last payment in the series.";
    }

    /** Everything the runner needs, detached from the persistence context. */
    public record DueOrder(
            UUID id,
            UUID ownerId,
            String ownerUsername,
            UUID sourceAccountId,
            String destinationAccountNumber,
            String payeeName,
            java.math.BigDecimal amount,
            com.minibank.common.money.CurrencyCode currency,
            String description,
            com.minibank.transfer.domain.TransactionCategory category,
            LocalDate dueOn) {}
}
