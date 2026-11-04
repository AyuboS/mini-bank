package com.minibank.moneyrequest.service;

import com.minibank.account.domain.BankAccount;
import com.minibank.account.repository.BankAccountRepository;
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
import com.minibank.moneyrequest.domain.MoneyRequest;
import com.minibank.moneyrequest.domain.MoneyRequestStatus;
import com.minibank.moneyrequest.dto.MoneyRequestCreate;
import com.minibank.moneyrequest.dto.PayMoneyRequest;
import com.minibank.moneyrequest.repository.MoneyRequestRepository;
import com.minibank.notification.center.domain.NotificationSeverity;
import com.minibank.notification.center.domain.NotificationType;
import com.minibank.notification.center.service.NotificationService;
import com.minibank.transfer.domain.TransactionCategory;
import com.minibank.transfer.domain.Transfer;
import com.minibank.transfer.dto.TransferRequest;
import com.minibank.transfer.repository.TransferRepository;
import com.minibank.transfer.service.TransferService;
import com.minibank.user.domain.User;
import com.minibank.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment requests: raising them, and settling them.
 *
 * <h2>Who can do what</h2>
 *
 * <p>The two roles are strictly separated, and each check is made against the token rather than
 * against anything in the request body:
 *
 * <ul>
 *   <li>the <b>requester</b> may cancel their own request, and nothing else
 *   <li>the <b>payer</b> — whoever holds the account the request names — may pay or decline it
 * </ul>
 *
 * <p>Paying goes through {@link TransferService}, initiated by the payer, so the payer's own limits,
 * balance and risk profile decide the outcome. A request cannot move money by existing.
 */
@Service
public class MoneyRequestService {

    private static final Logger log = LoggerFactory.getLogger(MoneyRequestService.class);

    /** How long a request stands if no expiry is given. */
    private static final int DEFAULT_EXPIRY_DAYS = 7;

    /** A ceiling on outstanding requests, so the feature cannot be turned into a spam channel. */
    private static final int MAX_PENDING_PER_USER = 25;

    private final MoneyRequestRepository requests;
    private final AccountService accountService;
    private final BankAccountRepository accounts;
    private final TransferService transferService;
    private final TransferRepository transfers;
    private final UserRepository users;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final Clock clock;

    public MoneyRequestService(
            MoneyRequestRepository requests,
            AccountService accountService,
            BankAccountRepository accounts,
            TransferService transferService,
            TransferRepository transfers,
            UserRepository users,
            AuditService auditService,
            NotificationService notificationService,
            Clock clock) {
        this.requests = requests;
        this.accountService = accountService;
        this.accounts = accounts;
        this.transferService = transferService;
        this.transfers = transfers;
        this.users = users;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MoneyRequest> outgoing(UUID userId) {
        return requests.findOutgoing(userId);
    }

    @Transactional(readOnly = true)
    public List<MoneyRequest> incoming(UUID userId) {
        List<String> myAccountNumbers = accounts.findAllByOwnerId(userId).stream()
                .map(BankAccount::getAccountNumber)
                .toList();
        return myAccountNumbers.isEmpty() ? List.of() : requests.findIncoming(myAccountNumbers);
    }

    @Transactional
    public MoneyRequest create(MoneyRequestCreate command, AuthenticatedUser caller) {
        User requester = users.findById(caller.getId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));

        if (requests.countByRequesterIdAndStatus(requester.getId(), MoneyRequestStatus.PENDING)
                >= MAX_PENDING_PER_USER) {
            throw new BusinessRuleException(
                    ErrorCode.CONFLICT, "You already have the maximum number of open requests");
        }

        BankAccount destination = accountService.getAccessibleAccount(command.toAccountId(), caller);
        if (!destination.isOwnedBy(caller.getId())) {
            throw new ForbiddenException("Money can only be requested into your own account");
        }

        // The payer's account must exist and match the currency, so a request that can never be paid
        // is refused at the point it is raised rather than when somebody tries to settle it.
        BankAccount payerAccount = accountService.getByAccountNumber(command.fromAccountNumber());
        if (payerAccount.getId().equals(destination.getId())) {
            throw new BusinessRuleException(ErrorCode.SAME_ACCOUNT_TRANSFER);
        }
        if (payerAccount.getCurrency() != destination.getCurrency()) {
            throw new BusinessRuleException(
                    ErrorCode.CURRENCY_MISMATCH,
                    "That account holds %s; ask from a %s account"
                            .formatted(payerAccount.getCurrency(), destination.getCurrency()));
        }

        BigDecimal amount = Money.requireValidAmount(command.amount(), destination.getCurrency());
        Instant expiresAt = clock.instant()
                .plus(Duration.ofDays(
                        command.expiresInDays() == null ? DEFAULT_EXPIRY_DAYS : command.expiresInDays()));

        MoneyRequest request = requests.save(MoneyRequest.open(
                requester,
                destination,
                payerAccount.getAccountNumber(),
                amount,
                command.description(),
                expiresAt));

        auditService.record(AuditEvent.success(AuditAction.MONEY_REQUEST_CREATED)
                .by(requester)
                .on("MoneyRequest", request.getId())
                .with("amount", amount)
                .with("currency", destination.getCurrency()));

        if (payerAccount.getOwner() != null) {
            notificationService.publish(
                    payerAccount.getOwner(),
                    NotificationType.TRANSACTION,
                    NotificationSeverity.INFO,
                    "%s asked you for %s%s"
                            .formatted(
                                    requester.fullName(),
                                    destination.getCurrency().symbol(),
                                    amount.stripTrailingZeros().toPlainString()),
                    command.description() == null
                            ? "Open the request to pay or decline it."
                            : command.description(),
                    "MoneyRequest",
                    request.getId());
        }
        log.info("Payment request {} raised for {} {}", request.getId(), amount, destination.getCurrency());
        return request;
    }

    /**
     * The payer settles the request.
     *
     * <p>The transfer is created by the payer, from an account the payer owns, with an idempotency
     * key derived from the request id — so a double tap on "Pay" cannot pay twice.
     */
    @Transactional
    public MoneyRequest pay(UUID requestId, PayMoneyRequest command, AuthenticatedUser caller) {
        MoneyRequest request = requests.findFullyById(requestId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.MONEY_REQUEST_NOT_FOUND));
        Instant now = clock.instant();

        BankAccount payerAccount = requirePayerAccount(request, command.fromAccountId(), caller);
        request.requireActionable(now);

        TransferService.TransferOutcome outcome = transferService.create(
                new TransferRequest(
                        payerAccount.getId(),
                        request.getRequesterAccount().getAccountNumber(),
                        request.getAmount(),
                        request.getCurrency(),
                        request.getDescription() == null
                                ? "Payment to " + request.getRequester().fullName()
                                : request.getDescription(),
                        TransactionCategory.TRANSFER),
                caller,
                "money-request:" + request.getId());

        Transfer settled = transfers
                .findById(outcome.transfer().id())
                .orElseThrow(() -> new NotFoundException(ErrorCode.TRANSFER_NOT_FOUND));
        request.markPaid(settled, now);

        auditService.record(AuditEvent.success(AuditAction.MONEY_REQUEST_PAID)
                .by(caller.getId(), caller.getUsername())
                .on("MoneyRequest", requestId)
                .with("reference", outcome.transfer().reference()));

        notificationService.publish(
                request.getRequester(),
                NotificationType.TRANSACTION,
                NotificationSeverity.SUCCESS,
                "Your request was paid",
                "%s%s arrived from %s."
                        .formatted(
                                request.getCurrency().symbol(),
                                request.getAmount().stripTrailingZeros().toPlainString(),
                                maskAccount(request.getPayerAccountNumber())),
                "Transfer",
                settled.getId());
        return request;
    }

    @Transactional
    public MoneyRequest decline(UUID requestId, AuthenticatedUser caller) {
        MoneyRequest request = requests.findFullyById(requestId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.MONEY_REQUEST_NOT_FOUND));
        requirePayer(request, caller);
        request.decline(clock.instant());

        auditService.record(AuditEvent.success(AuditAction.MONEY_REQUEST_DECLINED)
                .by(caller.getId(), caller.getUsername())
                .on("MoneyRequest", requestId));
        notificationService.publish(
                request.getRequester(),
                NotificationType.TRANSACTION,
                NotificationSeverity.INFO,
                "Your request was declined",
                "%s%s from %s was not paid."
                        .formatted(
                                request.getCurrency().symbol(),
                                request.getAmount().stripTrailingZeros().toPlainString(),
                                maskAccount(request.getPayerAccountNumber())),
                "MoneyRequest",
                request.getId());
        return request;
    }

    /** Withdraws a request. Only the person who raised it may do this. */
    @Transactional
    public MoneyRequest cancel(UUID requestId, AuthenticatedUser caller) {
        MoneyRequest request = requests.findFullyById(requestId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.MONEY_REQUEST_NOT_FOUND));
        if (!request.wasRaisedBy(caller.getId())) {
            // Reported as missing rather than forbidden: a payer probing ids learns nothing.
            throw new NotFoundException(ErrorCode.MONEY_REQUEST_NOT_FOUND);
        }
        request.cancel(clock.instant());
        auditService.record(AuditEvent.success(AuditAction.MONEY_REQUEST_CANCELLED)
                .by(caller.getId(), caller.getUsername())
                .on("MoneyRequest", requestId));
        return request;
    }

    /** The account the payer names must be theirs, and must be the one the request was addressed to. */
    private BankAccount requirePayerAccount(MoneyRequest request, UUID accountId, AuthenticatedUser caller) {
        BankAccount account = accountService.getAccessibleAccount(accountId, caller);
        if (!account.isOwnedBy(caller.getId())) {
            throw new ForbiddenException("You can only pay from your own account");
        }
        if (!account.getAccountNumber().equals(request.getPayerAccountNumber())) {
            throw new BusinessRuleException(
                    ErrorCode.FORBIDDEN, "This request was addressed to a different account");
        }
        return account;
    }

    private void requirePayer(MoneyRequest request, AuthenticatedUser caller) {
        boolean isPayer = accounts.findAllByOwnerId(caller.getId()).stream()
                .anyMatch(account -> account.getAccountNumber().equals(request.getPayerAccountNumber()));
        if (!isPayer) {
            throw new NotFoundException(ErrorCode.MONEY_REQUEST_NOT_FOUND);
        }
    }

    /** An account number reduced to its last four, for a notification the other party will read. */
    private static String maskAccount(String accountNumber) {
        return accountNumber == null || accountNumber.length() < 4
                ? "an account"
                : "••••" + accountNumber.substring(accountNumber.length() - 4);
    }
}
