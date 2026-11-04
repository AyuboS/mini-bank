package com.minibank.transfer.service;

import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.service.AuditEvent;
import com.minibank.audit.service.AuditService;
import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.error.NotFoundException;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.infrastructure.metrics.BankingMetrics;
import com.minibank.transfer.domain.Transfer;
import com.minibank.transfer.domain.TransferStatus;
import com.minibank.transfer.dto.TransferResponse;
import com.minibank.transfer.repository.TransferRepository;
import com.minibank.user.domain.User;
import com.minibank.user.repository.UserRepository;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Back-office operations on transfers: approving what the risk engine held, and reversing what
 * should not have happened.
 *
 * <p>Reversal never edits or deletes the original. It writes a second, opposite transfer linked to
 * the first, which is how a real ledger records a correction: the mistake and its remedy both remain
 * visible, and the account history a customer sees still adds up.
 */
@Service
public class TransferAdministrationService {

    private static final Logger log = LoggerFactory.getLogger(TransferAdministrationService.class);

    private final TransferRepository transferRepository;
    private final TransferExecutor transferExecutor;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final BankingMetrics metrics;
    private final Clock clock;

    public TransferAdministrationService(
            TransferRepository transferRepository,
            TransferExecutor transferExecutor,
            UserRepository userRepository,
            AuditService auditService,
            BankingMetrics metrics,
            Clock clock) {
        this.transferRepository = transferRepository;
        this.transferExecutor = transferExecutor;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.metrics = metrics;
        this.clock = clock;
    }

    /** Settles a transfer that was held for review. */
    public TransferResponse approve(UUID transferId, AuthenticatedUser admin) {
        Transfer pending = transferRepository
                .findWithAccountsById(transferId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.TRANSFER_NOT_FOUND));
        if (pending.getStatus() != TransferStatus.PENDING) {
            throw new BusinessRuleException(
                    ErrorCode.INVALID_TRANSFER_STATE,
                    "Only a PENDING transfer can be approved; %s is %s"
                            .formatted(pending.getReference(), pending.getStatus()));
        }

        Transfer settled = transferExecutor.settlePending(transferId);
        metrics.recordTransfer(TransferStatus.COMPLETED, settled.getCurrency(), settled.getAmount());
        auditService.recordIndependently(AuditEvent.success(AuditAction.TRANSFER_APPROVED)
                .by(admin.getId(), admin.getUsername())
                .on("Transfer", transferId)
                .with("reference", settled.getReference()));
        log.info("Transfer {} approved by {}", settled.getReference(), admin.getUsername());
        return TransferResponse.from(settled);
    }

    /** Refuses a transfer that was held for review. No money moves. */
    @Transactional
    public TransferResponse reject(UUID transferId, AuthenticatedUser admin, String reason) {
        Transfer pending = transferRepository
                .findWithAccountsById(transferId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.TRANSFER_NOT_FOUND));
        if (pending.getStatus() != TransferStatus.PENDING) {
            throw new BusinessRuleException(
                    ErrorCode.INVALID_TRANSFER_STATE,
                    "Only a PENDING transfer can be rejected; %s is %s"
                            .formatted(pending.getReference(), pending.getStatus()));
        }

        pending.fail("Rejected by administrator: " + reason, clock.instant());
        metrics.recordTransfer(TransferStatus.FAILED, pending.getCurrency(), pending.getAmount());
        auditService.record(AuditEvent.success(AuditAction.TRANSFER_REJECTED)
                .by(admin.getId(), admin.getUsername())
                .on("Transfer", transferId)
                .with("reference", pending.getReference())
                .with("reason", reason));
        log.info("Transfer {} rejected by {}", pending.getReference(), admin.getUsername());
        return TransferResponse.from(pending);
    }

    /** Reverses a settled transfer with compensating ledger entries. */
    public TransferResponse reverse(UUID transferId, AuthenticatedUser admin, String reason) {
        User adminUser = userRepository
                .findById(admin.getId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
        Transfer reversal = transferExecutor.reverse(transferId, adminUser, reason);
        metrics.recordTransfer(TransferStatus.COMPLETED, reversal.getCurrency(), reversal.getAmount());
        return TransferResponse.from(reversal);
    }
}
