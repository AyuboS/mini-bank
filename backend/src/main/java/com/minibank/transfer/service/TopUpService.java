package com.minibank.transfer.service;

import com.minibank.account.domain.BankAccount;
import com.minibank.account.service.AccountService;
import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.service.AuditEvent;
import com.minibank.audit.service.AuditService;
import com.minibank.common.error.ApiException;
import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.error.NotFoundException;
import com.minibank.common.money.Money;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.idempotency.service.IdempotencyService;
import com.minibank.infrastructure.metrics.BankingMetrics;
import com.minibank.transfer.config.TopUpProperties;
import com.minibank.transfer.domain.TransactionCategory;
import com.minibank.transfer.domain.Transfer;
import com.minibank.transfer.domain.TransferStatus;
import com.minibank.transfer.domain.TransferType;
import com.minibank.transfer.dto.TopUpRequest;
import com.minibank.transfer.dto.TransferResponse;
import com.minibank.transfer.repository.TransferRepository;
import com.minibank.user.domain.User;
import com.minibank.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Simulated top-ups: a customer adds play money to their own spending account.
 *
 * <p>It is the admin deposit path with the customer as initiator: the bank's system funding account
 * is debited and the customer's account credited through {@link TransferExecutor#settle}, so the
 * ledger stays balanced. Not {@code @Transactional}, for the same reason as {@link TransferService}:
 * the idempotency record and the settlement commit separately.
 */
@Service
public class TopUpService {

    public static final String DESCRIPTION = "Simulated top-up";
    private static final String ENDPOINT = "POST /api/v1/accounts/{id}/top-up";

    private final AccountService accountService;
    private final UserRepository userRepository;
    private final TransferExecutor transferExecutor;
    private final TransferRepository transferRepository;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final BankingMetrics metrics;
    private final TopUpProperties properties;
    private final Clock clock;

    public TopUpService(
            AccountService accountService,
            UserRepository userRepository,
            TransferExecutor transferExecutor,
            TransferRepository transferRepository,
            IdempotencyService idempotencyService,
            AuditService auditService,
            BankingMetrics metrics,
            TopUpProperties properties,
            Clock clock) {
        this.accountService = accountService;
        this.userRepository = userRepository;
        this.transferExecutor = transferExecutor;
        this.transferRepository = transferRepository;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.metrics = metrics;
        this.properties = properties;
        this.clock = clock;
    }

    public TransferService.TransferOutcome topUp(
            UUID accountId, TopUpRequest request, AuthenticatedUser caller, String idempotencyKey) {
        // The account id is part of the fingerprint, so one key cannot top up two accounts.
        IdempotencyService.Reservation reservation = idempotencyService.begin(
                idempotencyKey, caller.getId(), ENDPOINT, new Fingerprint(accountId, request.amount()));

        if (reservation instanceof IdempotencyService.Reservation.ReplaySuccess replay) {
            metrics.recordIdempotentReplay();
            return new TransferService.TransferOutcome(
                    idempotencyService.readResponse(replay.body(), TransferResponse.class),
                    true,
                    HttpStatus.valueOf(replay.status()));
        }
        if (reservation instanceof IdempotencyService.Reservation.ReplayFailure replay) {
            metrics.recordIdempotentReplay();
            throw new ApiException(ErrorCode.valueOf(replay.code()), replay.message());
        }

        UUID reservationId = ((IdempotencyService.Reservation.Proceed) reservation).reservationId();
        try {
            TransferResponse response = execute(accountId, request, caller);
            idempotencyService.complete(reservationId, 201, response, response.id());
            return new TransferService.TransferOutcome(response, false, HttpStatus.CREATED);
        } catch (ApiException ex) {
            idempotencyService.fail(reservationId, ex.getErrorCode(), ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            idempotencyService.release(reservationId);
            throw ex;
        }
    }

    private TransferResponse execute(UUID accountId, TopUpRequest request, AuthenticatedUser caller) {
        BankAccount account = accountService.getAccessibleAccount(accountId, caller);
        if (!account.isOwnedBy(caller.getId())) {
            // An administrator can read any account but tops up only their own; staff use deposit.
            throw new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        if (account.isSavingsAccount()) {
            throw new BusinessRuleException(ErrorCode.TOP_UP_NOT_ALLOWED);
        }
        account.requireTransactable();

        BigDecimal amount = Money.requireValidAmount(request.amount(), account.getCurrency());
        checkCaps(caller.getId(), account, amount);

        User customer = userRepository
                .findById(caller.getId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
        BankAccount systemAccount = accountService.getSystemAccount(account.getCurrency());

        Transfer transfer = transferExecutor.settle(new TransferExecutor.SettlementCommand(
                systemAccount.getId(),
                account.getId(),
                amount,
                customer,
                DESCRIPTION,
                null,
                TransferType.DEPOSIT,
                TransactionCategory.TRANSFER,
                null,
                0,
                List.of()));
        metrics.recordTransfer(TransferStatus.COMPLETED, account.getCurrency(), amount);
        auditService.recordIndependently(AuditEvent.success(AuditAction.TOP_UP_COMPLETED)
                .by(caller.getId(), caller.getUsername())
                .on("BankAccount", accountId)
                .with("amount", amount)
                .with("currency", account.getCurrency())
                .with("reference", transfer.getReference()));
        return TransferResponse.from(transfer);
    }

    private void checkCaps(UUID userId, BankAccount account, BigDecimal amount) {
        if (amount.compareTo(properties.perTopUpMax()) > 0) {
            throw new BusinessRuleException(
                    ErrorCode.TOP_UP_LIMIT_EXCEEDED,
                    "A single top-up can be at most %s %s"
                            .formatted(properties.perTopUpMax().toPlainString(), account.getCurrency()));
        }
        Instant startOfDay = clock.instant()
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
        BigDecimal today = transferRepository.sumTopUpsSince(userId, account.getCurrency(), startOfDay);
        if (today.add(amount).compareTo(properties.dailyMax()) > 0) {
            throw new BusinessRuleException(
                    ErrorCode.TOP_UP_LIMIT_EXCEEDED,
                    "Top-ups are limited to %s %s a day (%s already today)"
                            .formatted(
                                    properties.dailyMax().toPlainString(),
                                    account.getCurrency(),
                                    today.toPlainString()));
        }
    }

    private record Fingerprint(UUID accountId, BigDecimal amount) {}
}
