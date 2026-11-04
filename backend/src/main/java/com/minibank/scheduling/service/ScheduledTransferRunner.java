package com.minibank.scheduling.service;

import com.minibank.common.error.ApiException;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.scheduling.repository.ScheduledTransferRepository;
import com.minibank.transfer.dto.TransferRequest;
import com.minibank.transfer.service.TransferService;
import com.minibank.user.domain.UserRole;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Executes standing orders that have fallen due.
 *
 * <h2>What "executes" means here</h2>
 *
 * <p>A due order is put through {@link TransferService#create} — the same entry point a customer's
 * own payment uses. Limits, the risk engine, idempotency, row locking, the ledger and the audit
 * trail therefore apply unchanged. Nothing about a scheduled run is simulated or given a shortcut,
 * and an order that would be refused if typed by hand is refused here too.
 *
 * <h2>Why this class holds no transaction</h2>
 *
 * <p>Settlement joins whatever transaction is open. If the runner held one, a refused payment would
 * mark it rollback-only, and the failure record and advanced due date written afterwards would be
 * discarded at commit — leaving the order permanently due and retried on every tick. So the attempt
 * happens with nothing open, and all persistence goes through {@link ScheduledTransferBookkeeper},
 * whose short transactions the attempt cannot poison. See that class for the full account.
 *
 * <h2>Why it cannot pay twice</h2>
 *
 * <p>The idempotency key is derived from the order's id and the date it was due, so a second pass on
 * the same day — a restart, an overlapping tick, two instances briefly running at once — replays the
 * stored answer instead of moving money again. The due date advances whatever the outcome, which
 * closes the same door from the other side.
 */
@Component
public class ScheduledTransferRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTransferRunner.class);

    /**
     * How many due orders one tick will attempt.
     *
     * <p>Bounded so a backlog is worked through over several ticks rather than in one pass holding
     * connections for minutes. Anything left over is still due on the next tick.
     */
    private static final int BATCH_SIZE = 200;

    private final ScheduledTransferRepository orders;
    private final ScheduledTransferBookkeeper bookkeeper;
    private final TransferService transferService;
    private final Clock clock;

    public ScheduledTransferRunner(
            ScheduledTransferRepository orders,
            ScheduledTransferBookkeeper bookkeeper,
            TransferService transferService,
            Clock clock) {
        this.orders = orders;
        this.bookkeeper = bookkeeper;
        this.transferService = transferService;
        this.clock = clock;
    }

    /**
     * Attempts every order due today.
     *
     * <p>Runs every ten minutes rather than once at midnight: a single daily firing means a
     * deployment during that minute silently skips a day's payments, and there is no cost to
     * checking again — an order that has already run today is no longer due.
     */
    @Scheduled(fixedDelayString = "${minibank.scheduling.run-interval-ms:600000}", initialDelayString = "60000")
    public void runDueTransfers() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<UUID> due = orders.findDueIds(today, PageRequest.of(0, BATCH_SIZE));
        if (due.isEmpty()) {
            return;
        }

        log.info("Standing orders: {} due on {}", due.size(), today);
        int settled = 0;
        int refused = 0;
        for (UUID id : due) {
            // Never let one order stop the batch. A single unreadable row must not cost every
            // later customer their payment.
            try {
                if (runOne(id, today)) {
                    settled++;
                } else {
                    refused++;
                }
            } catch (RuntimeException ex) {
                refused++;
                log.error("Standing order {} could not be processed", id, ex);
            }
        }
        log.info("Standing orders: {} settled, {} refused", settled, refused);
    }

    /**
     * Attempts one order.
     *
     * <p>Not transactional, by design — see the class comment.
     *
     * @return true when money moved
     */
    public boolean runOne(UUID orderId, LocalDate today) {
        Optional<ScheduledTransferBookkeeper.DueOrder> maybeOrder = bookkeeper.loadDue(orderId, today);
        if (maybeOrder.isEmpty()) {
            return false;
        }
        ScheduledTransferBookkeeper.DueOrder order = maybeOrder.get();

        TransferRequest request = new TransferRequest(
                order.sourceAccountId(),
                order.destinationAccountNumber(),
                order.amount(),
                order.currency(),
                order.description(),
                order.category());

        try {
            TransferService.TransferOutcome outcome =
                    transferService.create(request, asPrincipal(order), idempotencyKey(order.id(), order.dueOn()));
            bookkeeper.recordSuccess(
                    order.id(), order.dueOn(), outcome.transfer().id(), outcome.transfer().reference());
            return true;

        } catch (ApiException ex) {
            // A refusal is an answer, not a crash: insufficient funds, a frozen account, a risk
            // block. It is recorded against the order, the customer is told, and the schedule moves
            // on to its next date rather than retrying into an empty account all day.
            bookkeeper.recordFailure(order.id(), order.dueOn(), ex.getErrorCode().name(), ex.getMessage());
            log.warn("Standing order {} refused: {}", order.id(), ex.getErrorCode());
            return false;
        }
    }

    /**
     * A key that is the same for every attempt at the same run, and different for the next one.
     *
     * <p>This is what makes the runner safe to fire twice: {@code TransferService} recognises the
     * repeat and returns the answer it already gave rather than sending the money again.
     */
    private static String idempotencyKey(UUID orderId, LocalDate dueOn) {
        return "standing-order:%s:%s".formatted(orderId, dueOn);
    }

    /**
     * Runs on behalf of the order's owner.
     *
     * <p>Always a customer principal, never an elevated one: a standing order must be subject to
     * exactly the limits and checks its owner is subject to, and there is nothing here to escalate.
     */
    private static AuthenticatedUser asPrincipal(ScheduledTransferBookkeeper.DueOrder order) {
        return new AuthenticatedUser(order.ownerId(), order.ownerUsername(), UserRole.CUSTOMER, true);
    }
}
