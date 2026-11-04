package com.minibank.account.service;

import com.minibank.account.domain.AccountPurpose;
import com.minibank.account.domain.AccountStatus;
import com.minibank.account.domain.AccountType;
import com.minibank.account.domain.BankAccount;
import com.minibank.account.repository.BankAccountRepository;
import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.service.AuditEvent;
import com.minibank.audit.service.AuditService;
import com.minibank.common.error.ForbiddenException;
import com.minibank.common.error.NotFoundException;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.money.CurrencyCode;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.user.domain.User;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opening, reading and administering bank accounts.
 *
 * <p>Every read that takes an account id goes through {@link #getAccessibleAccount}, which is the
 * single place ownership is enforced. Scattering that check across controllers is how insecure
 * direct object references get introduced: one endpoint is written without it and nobody notices.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final BankAccountRepository bankAccountRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final AuditService auditService;

    public AccountService(
            BankAccountRepository bankAccountRepository,
            AccountNumberGenerator accountNumberGenerator,
            AuditService auditService) {
        this.bankAccountRepository = bankAccountRepository;
        this.accountNumberGenerator = accountNumberGenerator;
        this.auditService = auditService;
    }

    /** Opens a new customer spending account in the given currency. */
    @Transactional
    public BankAccount openAccount(User owner, CurrencyCode currency) {
        return openAccount(owner, currency, AccountPurpose.SPENDING);
    }

    /**
     * Opens a customer account with an explicit purpose.
     *
     * <p>The savings variant is used when a goal is created: a goal is backed by a real account, so
     * that contributing to it is an ordinary settled transfer rather than a number kept somewhere
     * off to the side.
     */
    @Transactional
    public BankAccount openAccount(User owner, CurrencyCode currency, AccountPurpose purpose) {
        BankAccount account =
                BankAccount.openCustomerAccount(owner, accountNumberGenerator.generate(), currency, purpose);
        BankAccount saved = bankAccountRepository.save(account);
        auditService.record(AuditEvent.success(AuditAction.ACCOUNT_CREATED)
                .by(owner)
                .on("BankAccount", saved.getId())
                .with("accountNumber", saved.getAccountNumber())
                .with("currency", currency)
                .with("purpose", purpose));
        log.info("Opened {} account {} for user {}", currency, saved.getAccountNumber(), owner.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<BankAccount> listOwnedAccounts(UUID ownerId) {
        return bankAccountRepository.findAllByOwnerId(ownerId);
    }

    /**
     * Loads an account the caller is allowed to see.
     *
     * <p>A customer requesting somebody else's account gets {@code ACCOUNT_NOT_FOUND} rather than
     * {@code FORBIDDEN}: telling them the id exists but is not theirs would confirm the existence of
     * another customer's account.
     */
    @Transactional(readOnly = true)
    public BankAccount getAccessibleAccount(UUID accountId, AuthenticatedUser caller) {
        BankAccount account = bankAccountRepository
                .findById(accountId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (caller.isAdmin()) {
            return account;
        }
        if (!account.isOwnedBy(caller.getId())) {
            log.warn("User {} attempted to access account {} they do not own", caller.getId(), accountId);
            throw new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        return account;
    }

    /** Loads an account by id without any ownership check. For internal and administrative use. */
    @Transactional(readOnly = true)
    public BankAccount getById(UUID accountId) {
        return bankAccountRepository
                .findById(accountId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public BankAccount getByAccountNumber(String accountNumber) {
        return bankAccountRepository
                .findByAccountNumber(accountNumber)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    /** Loads several accounts by number in one query, with their owners already fetched. */
    @Transactional(readOnly = true)
    public List<BankAccount> findAllByAccountNumbers(java.util.Collection<String> accountNumbers) {
        return accountNumbers.isEmpty()
                ? List.of()
                : bankAccountRepository.findAllByAccountNumberInWithOwner(accountNumbers);
    }

    /** The bank's own funding account for a currency, used as the counterparty for deposits. */
    @Transactional(readOnly = true)
    public BankAccount getSystemAccount(CurrencyCode currency) {
        return bankAccountRepository
                .findByAccountTypeAndCurrency(AccountType.SYSTEM, currency)
                .orElseThrow(() -> new IllegalStateException(
                        "No system account configured for currency " + currency));
    }

    @Transactional(readOnly = true)
    public Page<BankAccount> listAllCustomerAccounts(Pageable pageable) {
        return bankAccountRepository.findAllCustomerAccounts(pageable);
    }

    /** Freezes an account. Frozen accounts can neither send nor receive money. */
    @Transactional
    public BankAccount freeze(UUID accountId, AuthenticatedUser admin) {
        BankAccount account = requireCustomerAccount(accountId);
        account.setStatus(AccountStatus.FROZEN);
        auditService.record(AuditEvent.success(AuditAction.ACCOUNT_FROZEN)
                .by(admin.getId(), admin.getUsername())
                .on("BankAccount", accountId)
                .with("accountNumber", account.getAccountNumber()));
        log.info("Account {} frozen by admin {}", account.getAccountNumber(), admin.getUsername());
        return account;
    }

    @Transactional
    public BankAccount unfreeze(UUID accountId, AuthenticatedUser admin) {
        BankAccount account = requireCustomerAccount(accountId);
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new com.minibank.common.error.BusinessRuleException(
                    ErrorCode.ACCOUNT_CLOSED, "A closed account cannot be reactivated");
        }
        account.setStatus(AccountStatus.ACTIVE);
        auditService.record(AuditEvent.success(AuditAction.ACCOUNT_UNFROZEN)
                .by(admin.getId(), admin.getUsername())
                .on("BankAccount", accountId)
                .with("accountNumber", account.getAccountNumber()));
        log.info("Account {} unfrozen by admin {}", account.getAccountNumber(), admin.getUsername());
        return account;
    }

    private BankAccount requireCustomerAccount(UUID accountId) {
        BankAccount account = getById(accountId);
        if (account.isSystemAccount()) {
            // System accounts underpin the ledger; freezing one would break every deposit.
            throw new ForbiddenException("System accounts cannot be administered through this endpoint");
        }
        return account;
    }
}
