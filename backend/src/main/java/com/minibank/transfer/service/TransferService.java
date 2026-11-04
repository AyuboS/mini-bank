package com.minibank.transfer.service;

import com.minibank.account.domain.BankAccount;
import com.minibank.account.service.AccountService;
import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.service.AuditEvent;
import com.minibank.audit.service.AuditService;
import com.minibank.common.error.ApiException;
import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.error.ForbiddenException;
import com.minibank.common.error.NotFoundException;
import com.minibank.common.money.Money;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.idempotency.service.IdempotencyService;
import com.minibank.infrastructure.metrics.BankingMetrics;
import com.minibank.limit.service.TransferLimitService;
import com.minibank.risk.domain.RiskAssessment;
import com.minibank.risk.domain.RiskContext;
import com.minibank.risk.dto.RiskAssessmentResponse;
import com.minibank.risk.service.RiskEngine;
import com.minibank.transfer.domain.TransactionCategory;
import com.minibank.notification.center.service.TransferNotifier;
import com.minibank.transfer.domain.Transfer;
import com.minibank.transfer.repository.TransferRepository;
import com.minibank.transfer.domain.TransferStatus;
import com.minibank.transfer.domain.TransferType;
import com.minibank.transfer.dto.DepositRequest;
import com.minibank.transfer.dto.TransferRequest;
import com.minibank.transfer.dto.TransferResponse;
import com.minibank.user.domain.User;
import com.minibank.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the creation of a transfer.
 *
 * <p>Not {@code @Transactional}. This method coordinates several transactions that must commit
 * independently — the idempotency reservation, the settlement itself, and the record of a refusal —
 * and a single enclosing transaction would collapse them into one all-or-nothing unit, which is
 * precisely what must not happen. The atomic part of a transfer lives in {@link TransferExecutor}.
 *
 * <p>The order of the steps is deliberate: identity, then ownership, then domain validity, then
 * limits, then risk, then settlement. Each stage is cheaper than the next and rejects the request
 * with a more specific error, so a customer sending money to a closed account is told exactly that
 * rather than receiving a generic risk refusal.
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private static final String TRANSFER_ENDPOINT = "POST /api/v1/transfers";

    /**
     * Statuses whose money counts as spent for a budget.
     *
     * <p>The same set the card and transfer limits use: money awaiting review has already been
     * committed by the customer, and a budget that ignores it would tell them they have room they
     * do not.
     */
    private static final java.util.List<TransferStatus> SPENT_STATUSES =
            java.util.List.of(TransferStatus.PENDING, TransferStatus.PROCESSING, TransferStatus.COMPLETED);

    private final AccountService accountService;
    private final UserRepository userRepository;
    private final TransferExecutor transferExecutor;
    private final TransferLimitService transferLimitService;
    private final RiskEngine riskEngine;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final BankingMetrics metrics;
    private final TransactionCategorizer categorizer;
    private final TransferNotifier transferNotifier;
    private final TransferRepository transferRepository;
    private final Clock clock;

    public TransferService(
            AccountService accountService,
            UserRepository userRepository,
            TransferExecutor transferExecutor,
            TransferLimitService transferLimitService,
            RiskEngine riskEngine,
            IdempotencyService idempotencyService,
            AuditService auditService,
            BankingMetrics metrics,
            TransactionCategorizer categorizer,
            TransferNotifier transferNotifier,
            TransferRepository transferRepository,
            Clock clock) {
        this.accountService = accountService;
        this.userRepository = userRepository;
        this.transferExecutor = transferExecutor;
        this.transferLimitService = transferLimitService;
        this.riskEngine = riskEngine;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.metrics = metrics;
        this.categorizer = categorizer;
        this.transferNotifier = transferNotifier;
        this.transferRepository = transferRepository;
        this.clock = clock;
    }

    /**
     * Creates a transfer, guarded by the caller's idempotency key.
     *
     * @return the outcome, including whether it was replayed from a previous identical request
     */
    public TransferOutcome create(TransferRequest request, AuthenticatedUser caller, String idempotencyKey) {
        IdempotencyService.Reservation reservation =
                idempotencyService.begin(idempotencyKey, caller.getId(), TRANSFER_ENDPOINT, request);

        if (reservation instanceof IdempotencyService.Reservation.ReplaySuccess replay) {
            metrics.recordIdempotentReplay();
            auditService.recordIndependently(AuditEvent.success(AuditAction.IDEMPOTENT_REPLAY)
                    .by(caller.getId(), caller.getUsername())
                    .on("Transfer", replay.resourceId()));
            log.info("Returning stored response for replayed idempotency key (user {})", caller.getId());
            return new TransferOutcome(
                    idempotencyService.readResponse(replay.body(), TransferResponse.class),
                    true,
                    HttpStatus.valueOf(replay.status()));
        }

        if (reservation instanceof IdempotencyService.Reservation.ReplayFailure replay) {
            metrics.recordIdempotentReplay();
            // The client asked the same question twice; they get the same answer.
            throw new ApiException(ErrorCode.valueOf(replay.code()), replay.message());
        }

        UUID reservationId = ((IdempotencyService.Reservation.Proceed) reservation).reservationId();
        try {
            TransferOutcome outcome = execute(request, caller);
            idempotencyService.complete(
                    reservationId, outcome.status().value(), outcome.transfer(), outcome.transfer().id());
            return outcome;
        } catch (ApiException ex) {
            // A business refusal is a real answer, so it is stored and replayed on retry.
            idempotencyService.fail(reservationId, ex.getErrorCode(), ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            // An infrastructure failure is not an answer. Releasing the key lets the client retry,
            // which is the entire reason they sent one.
            idempotencyService.release(reservationId);
            throw ex;
        }
    }

    private TransferOutcome execute(TransferRequest request, AuthenticatedUser caller) {
        User initiator = userRepository
                .findById(caller.getId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));

        BankAccount source = accountService.getAccessibleAccount(request.fromAccountId(), caller);
        if (source.isSystemAccount()) {
            throw new ForbiddenException("System accounts cannot be used as a transfer source");
        }
        if (!source.isOwnedBy(caller.getId())) {
            // An administrator may read any account, but may not spend from one.
            throw new ForbiddenException("Transfers may only be initiated from your own accounts");
        }
        BankAccount destination = accountService.getByAccountNumber(request.toAccountNumber());

        BigDecimal amount = validate(request, source, destination);
        transferLimitService.checkWithinLimits(initiator, amount, source.getCurrency());

        // Derived once, here, so that every outcome below -- settled, held, blocked -- carries the
        // same label. A category assigned only on the happy path would leave the analytics blind to
        // exactly the payments a customer is most likely to go looking for.
        TransactionCategory category = categorizer
                .categorize(request.category(), request.description(), TransferType.INTERNAL)
                .orElse(null);

        RiskAssessment assessment = riskEngine.assess(
                new RiskContext(initiator, source, destination, amount, source.getCurrency(), clock.instant()));
        metrics.recordRiskDecision(assessment.decision());

        TransferExecutor.SettlementCommand command = new TransferExecutor.SettlementCommand(
                source.getId(),
                destination.getId(),
                amount,
                initiator,
                request.description(),
                null,
                TransferType.INTERNAL,
                category,
                assessment.decision().name(),
                assessment.riskScore(),
                assessment.reasons());

        return switch (assessment.decision()) {
            case BLOCK -> {
                String reason = String.join("; ", assessment.reasons());
                Transfer blocked =
                        transferExecutor.recordUnsettled(command, TransferStatus.FAILED, reason);
                metrics.recordTransfer(TransferStatus.FAILED, source.getCurrency(), amount);
                log.warn("Transfer {} blocked by risk engine: {}", blocked.getReference(), reason);
                throw new BusinessRuleException(
                        ErrorCode.RISK_BLOCKED,
                        "Transfer was blocked by the risk engine: " + reason);
            }
            case REVIEW -> {
                String reason = String.join("; ", assessment.reasons());
                Transfer held = transferExecutor.recordUnsettled(command, TransferStatus.PENDING, reason);
                metrics.recordTransfer(TransferStatus.PENDING, source.getCurrency(), amount);
                log.info("Transfer {} held for manual review: {}", held.getReference(), reason);
                yield new TransferOutcome(
                        TransferResponse.from(held, RiskAssessmentResponse.from(assessment)),
                        false,
                        HttpStatus.ACCEPTED);
            }
            case ALLOW -> {
                Transfer settled;
                try {
                    settled = transferExecutor.settle(command);
                } catch (BusinessRuleException ex) {
                    // Settlement rolled back, so nothing moved. The attempt still has to be
                    // visible: an insufficient-funds rejection that leaves no trace is
                    // indistinguishable from a request that never arrived, both to the customer
                    // asking why their payment failed and to anyone reviewing the account later.
                    // recordUnsettled commits in its own transaction for exactly that reason.
                    transferExecutor.recordUnsettled(command, TransferStatus.FAILED, ex.getMessage());
                    metrics.recordTransfer(TransferStatus.FAILED, source.getCurrency(), amount);
                    log.warn("Transfer from {} failed: {}", source.getAccountNumber(), ex.getMessage());
                    throw ex;
                }
                metrics.recordTransfer(TransferStatus.COMPLETED, source.getCurrency(), amount);
                // Best-effort, and outside the settlement transaction's critical path: the payment
                // has already committed, so a notification failure here must not surface to the
                // customer as a failed transfer. TransferNotifier swallows its own errors.
                notifyQuietly(settled, category, initiator.getId(), source);
                yield new TransferOutcome(
                        TransferResponse.from(settled, RiskAssessmentResponse.from(assessment)),
                        false,
                        HttpStatus.CREATED);
            }
        };
    }

    /**
     * Domain validation of a proposed transfer.
     *
     * @return the amount, normalised to the storage scale
     */
    private BigDecimal validate(TransferRequest request, BankAccount source, BankAccount destination) {
        if (source.getId().equals(destination.getId())) {
            throw new BusinessRuleException(ErrorCode.SAME_ACCOUNT_TRANSFER);
        }
        if (request.currency() != source.getCurrency()) {
            throw new BusinessRuleException(
                    ErrorCode.CURRENCY_MISMATCH,
                    "Requested currency %s does not match the source account currency %s"
                            .formatted(request.currency(), source.getCurrency()));
        }
        if (source.getCurrency() != destination.getCurrency()) {
            // No FX in this system by design: converting money is a separate concern with its own
            // rate sourcing, spread and rounding rules, and faking it would misrepresent balances.
            throw new BusinessRuleException(
                    ErrorCode.CURRENCY_MISMATCH,
                    "Cannot transfer between a %s account and a %s account"
                            .formatted(source.getCurrency(), destination.getCurrency()));
        }
        source.requireTransactable();
        destination.requireTransactable();
        return Money.requireValidAmount(request.amount(), source.getCurrency());
    }

    /**
     * Credits a customer account from the bank's system funding account.
     *
     * <p>Administrative, and deliberately outside the risk and limit checks, which exist to constrain
     * customers rather than the bank's own operations. It is still an ordinary double-entry movement:
     * the system account is debited by the same amount.
     */
    public TransferResponse deposit(UUID accountId, DepositRequest request, AuthenticatedUser admin) {
        BankAccount destination = accountService.getById(accountId);
        if (destination.isSystemAccount()) {
            throw new ForbiddenException("Cannot deposit into a system account");
        }
        destination.requireTransactable();

        BankAccount systemAccount = accountService.getSystemAccount(destination.getCurrency());
        User adminUser = userRepository
                .findById(admin.getId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
        BigDecimal amount = Money.requireValidAmount(request.amount(), destination.getCurrency());

        Transfer transfer = transferExecutor.settle(new TransferExecutor.SettlementCommand(
                systemAccount.getId(),
                destination.getId(),
                amount,
                adminUser,
                request.description() == null ? "Deposit" : request.description(),
                null,
                TransferType.DEPOSIT,
                categorizer
                        .categorize(null, request.description(), TransferType.DEPOSIT)
                        .orElse(TransactionCategory.TRANSFER),
                null,
                0,
                java.util.List.of()));
        metrics.recordTransfer(TransferStatus.COMPLETED, destination.getCurrency(), amount);
        auditService.recordIndependently(AuditEvent.success(AuditAction.DEPOSIT_COMPLETED)
                .by(admin.getId(), admin.getUsername())
                .on("BankAccount", accountId)
                .with("amount", amount)
                .with("reference", transfer.getReference()));
        return TransferResponse.from(transfer);
    }

    /**
     * Publishes the settlement notifications without letting them affect the payment.
     *
     * <p>The budget figure passed to the notifier is this month's outgoing total in the same
     * category from the same account, read after settlement so it includes the payment that just
     * happened. Computing it here rather than inside the notifier keeps the query on a path that
     * already knows the account and the window.
     */
    private void notifyQuietly(
            Transfer settled, TransactionCategory category, java.util.UUID initiatorId, BankAccount source) {
        try {
            BigDecimal spentThisMonth = category == null
                    ? null
                    : transferRepository.sumOutgoingByAccountSinceForCategory(
                            source.getId(), startOfMonth(), SPENT_STATUSES, category);
            transferNotifier.settled(settled.getId(), spentThisMonth);
        } catch (RuntimeException ex) {
            log.error("Could not publish notifications for transfer {}", settled.getReference(), ex);
        }
    }

    /** Midnight on the first of the current month, UTC. Matches how card spending windows are cut. */
    private java.time.Instant startOfMonth() {
        java.time.LocalDate today = java.time.LocalDate.ofInstant(clock.instant(), java.time.ZoneOffset.UTC);
        return today.withDayOfMonth(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
    }

    /** The result of creating a transfer, together with the status the API should return. */
    public record TransferOutcome(TransferResponse transfer, boolean replayed, HttpStatus status) {}
}
