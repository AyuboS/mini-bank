package com.minibank.transfer.service;

import com.minibank.account.domain.BankAccount;
import com.minibank.account.repository.BankAccountRepository;
import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.service.AuditEvent;
import com.minibank.audit.service.AuditService;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.error.NotFoundException;
import com.minibank.ledger.service.LedgerService;
import com.minibank.transfer.domain.TransactionCategory;
import com.minibank.transfer.domain.Transfer;
import com.minibank.transfer.domain.TransferStatus;
import com.minibank.transfer.domain.TransferType;
import com.minibank.transfer.repository.TransferRepository;
import com.minibank.user.domain.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settlement: the part of a transfer that must be atomic.
 *
 * <h2>Why locking, and why in this order</h2>
 *
 * <p>Two concurrent transfers from the same account will both read the same balance if nothing stops
 * them, both conclude there are sufficient funds, and both commit — the classic double spend. This
 * class prevents that by taking a {@code SELECT ... FOR UPDATE} row lock on each account before
 * reading its balance. The second transaction blocks at that point and, when it resumes, reads the
 * balance the first one committed.
 *
 * <p>Locking two rows introduces a second hazard: if A→B locks A then B while B→A locks B then A,
 * the two deadlock. {@link #lockInDeterministicOrder} removes the cycle by always taking the locks
 * in ascending account-id order, regardless of which account is sending. With a total order over
 * the resources, a cycle cannot form.
 *
 * <p>Pessimistic rather than optimistic locking is the right trade here: contention on a single
 * account is real (a salary run, a retry storm), and an optimistic strategy would answer it with a
 * burst of retries and version conflicts surfaced to the customer. Blocking briefly is cheaper than
 * failing a payment. The {@code @Version} column remains on the entity as a second line of defence
 * for paths that update an account without going through settlement.
 *
 * <p>Every mutation below happens in one transaction: the transfer row, both balances and both
 * ledger entries commit together or not at all. A crash midway leaves no half-moved money.
 */
@Service
public class TransferExecutor {

    private static final Logger log = LoggerFactory.getLogger(TransferExecutor.class);

    private final BankAccountRepository bankAccountRepository;
    private final TransferRepository transferRepository;
    private final LedgerService ledgerService;
    private final AuditService auditService;
    private final TransferReferenceGenerator referenceGenerator;
    private final Clock clock;

    public TransferExecutor(
            BankAccountRepository bankAccountRepository,
            TransferRepository transferRepository,
            LedgerService ledgerService,
            AuditService auditService,
            TransferReferenceGenerator referenceGenerator,
            Clock clock) {
        this.bankAccountRepository = bankAccountRepository;
        this.transferRepository = transferRepository;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
        this.referenceGenerator = referenceGenerator;
        this.clock = clock;
    }

    /**
     * Creates and settles a transfer atomically.
     *
     * @throws com.minibank.common.error.BusinessRuleException if, once the accounts are locked, the
     *     source cannot cover the amount or either account has stopped being transactable
     */
    @Transactional
    public Transfer settle(SettlementCommand command) {
        Instant now = clock.instant();
        LockedPair accounts = lockInDeterministicOrder(command.sourceAccountId(), command.destinationAccountId());

        // Re-checked under the lock, not before it. State read before locking is a guess: an
        // administrator could have frozen the account in the meantime.
        accounts.source().requireTransactable();
        accounts.destination().requireTransactable();

        Transfer transfer = Transfer.initiate(
                referenceGenerator.generate(),
                command.type(),
                accounts.source(),
                accounts.destination(),
                command.amount(),
                command.initiator(),
                command.description(),
                command.idempotencyKey());
        transfer.setCategory(command.category());
        transfer.applyRiskOutcome(command.riskDecision(), command.riskScore(), command.riskReasons());
        transferRepository.save(transfer);

        transfer.transitionTo(TransferStatus.PROCESSING, now);
        // Throws if the source cannot cover the amount; the whole transaction then rolls back.
        ledgerService.postDoubleEntry(transfer, accounts.source(), accounts.destination(), command.amount());
        transfer.transitionTo(TransferStatus.COMPLETED, now);

        auditService.record(AuditEvent.success(AuditAction.TRANSFER_COMPLETED)
                .by(command.initiator())
                .on("Transfer", transfer.getId())
                .with("reference", transfer.getReference())
                .with("amount", command.amount())
                .with("currency", transfer.getCurrency())
                .with("from", accounts.source().getAccountNumber())
                .with("to", accounts.destination().getAccountNumber()));

        log.info(
                "Transfer {} settled: {} {} from {} to {}",
                transfer.getReference(),
                command.amount(),
                transfer.getCurrency(),
                accounts.source().getAccountNumber(),
                accounts.destination().getAccountNumber());
        return transfer;
    }

    /**
     * Persists a transfer that will not settle now: held for review, or refused.
     *
     * <p>Runs in its own transaction so the record survives the caller rolling back — a refused
     * transfer that leaves no trace is indistinguishable from one that was never attempted.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transfer recordUnsettled(SettlementCommand command, TransferStatus targetStatus, String reason) {
        Instant now = clock.instant();
        BankAccount source = bankAccountRepository
                .findById(command.sourceAccountId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));
        BankAccount destination = bankAccountRepository
                .findById(command.destinationAccountId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));

        Transfer transfer = Transfer.initiate(
                referenceGenerator.generate(),
                command.type(),
                source,
                destination,
                command.amount(),
                command.initiator(),
                command.description(),
                command.idempotencyKey());
        transfer.setCategory(command.category());
        transfer.applyRiskOutcome(command.riskDecision(), command.riskScore(), command.riskReasons());

        transferRepository.save(transfer);
        if (targetStatus == TransferStatus.FAILED) {
            // Through the state machine rather than by assigning the field, so this path obeys the
            // same transition rules as every other.
            transfer.fail(reason, now);
        }

        AuditAction action = targetStatus == TransferStatus.FAILED
                ? AuditAction.TRANSFER_FAILED
                : AuditAction.TRANSFER_FLAGGED_FOR_REVIEW;
        auditService.record(AuditEvent.failure(action)
                .by(command.initiator())
                .on("Transfer", transfer.getId())
                .with("reference", transfer.getReference())
                .with("reason", reason)
                .with("amount", command.amount()));
        return transfer;
    }

    /**
     * Settles a transfer that was previously held in PENDING for review.
     *
     * <p>The state machine is what makes this safe to expose: a transfer that already reached
     * COMPLETED or FAILED cannot be walked back into PROCESSING, so approving twice is rejected
     * rather than paying twice.
     */
    @Transactional
    public Transfer settlePending(UUID transferId) {
        Instant now = clock.instant();
        Transfer transfer = transferRepository
                .findWithAccountsById(transferId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.TRANSFER_NOT_FOUND));

        LockedPair accounts = lockInDeterministicOrder(
                transfer.getSourceAccount().getId(), transfer.getDestinationAccount().getId());
        accounts.source().requireTransactable();
        accounts.destination().requireTransactable();

        transfer.transitionTo(TransferStatus.PROCESSING, now);
        ledgerService.postDoubleEntry(transfer, accounts.source(), accounts.destination(), transfer.getAmount());
        transfer.transitionTo(TransferStatus.COMPLETED, now);
        return transfer;
    }

    /**
     * Reverses a settled transfer by posting the opposite entries.
     *
     * <p>The original transfer is not edited or deleted. A reversal is itself a transfer, linked back
     * to the one it undoes, so the ledger keeps both movements and the history remains a faithful
     * account of what happened.
     */
    @Transactional
    public Transfer reverse(UUID originalTransferId, User admin, String reason) {
        Instant now = clock.instant();
        Transfer original = transferRepository
                .findWithAccountsById(originalTransferId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.TRANSFER_NOT_FOUND));

        if (original.getStatus() != TransferStatus.COMPLETED) {
            throw new com.minibank.common.error.BusinessRuleException(
                    ErrorCode.INVALID_TRANSFER_STATE,
                    "Only a COMPLETED transfer can be reversed; %s is %s"
                            .formatted(original.getReference(), original.getStatus()));
        }
        if (transferRepository.existsByReversalOfId(originalTransferId)) {
            throw new com.minibank.common.error.BusinessRuleException(ErrorCode.TRANSFER_ALREADY_REVERSED);
        }

        // The reversal moves money the other way, so the original destination is now the source.
        LockedPair accounts = lockInDeterministicOrder(
                original.getDestinationAccount().getId(), original.getSourceAccount().getId());

        Transfer reversal = Transfer.initiate(
                referenceGenerator.generate(),
                TransferType.REVERSAL,
                accounts.forId(original.getDestinationAccount().getId()),
                accounts.forId(original.getSourceAccount().getId()),
                original.getAmount(),
                admin,
                "Reversal of " + original.getReference(),
                null);
        reversal.setReversalOf(original);
        transferRepository.save(reversal);

        reversal.transitionTo(TransferStatus.PROCESSING, now);
        ledgerService.postDoubleEntry(
                reversal,
                accounts.forId(original.getDestinationAccount().getId()),
                accounts.forId(original.getSourceAccount().getId()),
                original.getAmount());
        reversal.transitionTo(TransferStatus.COMPLETED, now);
        original.transitionTo(TransferStatus.REVERSED, now);

        auditService.record(AuditEvent.success(AuditAction.TRANSFER_REVERSED)
                .by(admin)
                .on("Transfer", original.getId())
                .with("originalReference", original.getReference())
                .with("reversalReference", reversal.getReference())
                .with("reason", reason));
        log.info("Transfer {} reversed by {} as {}", original.getReference(), admin.getUsername(), reversal.getReference());
        return reversal;
    }

    /**
     * Locks both accounts, always in ascending id order, and returns them keyed by role.
     *
     * <p>See the class comment: the ordering is what makes concurrent opposing transfers deadlock
     * free.
     */
    private LockedPair lockInDeterministicOrder(UUID sourceId, UUID destinationId) {
        List<UUID> ordered = java.util.stream.Stream.of(sourceId, destinationId)
                .sorted()
                .toList();
        BankAccount first = lock(ordered.get(0));
        BankAccount second = lock(ordered.get(1));
        BankAccount source = first.getId().equals(sourceId) ? first : second;
        BankAccount destination = first.getId().equals(destinationId) ? first : second;
        return new LockedPair(source, destination);
    }

    private BankAccount lock(UUID accountId) {
        return bankAccountRepository
                .findByIdForUpdate(accountId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    /** Two accounts held under row locks for the duration of the transaction. */
    private record LockedPair(BankAccount source, BankAccount destination) {

        BankAccount forId(UUID id) {
            return source.getId().equals(id) ? source : destination;
        }
    }

    /** Everything settlement needs, already validated by the calling service. */
    public record SettlementCommand(
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal amount,
            User initiator,
            String description,
            String idempotencyKey,
            TransferType type,
            TransactionCategory category,
            String riskDecision,
            int riskScore,
            java.util.List<String> riskReasons) {}
}
