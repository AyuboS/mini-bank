package com.minibank.account.domain;

import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.money.CurrencyCode;
import com.minibank.common.money.Money;
import com.minibank.common.persistence.AuditedEntity;
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
import lombok.Getter;
import lombok.Setter;

/**
 * A bank account holding a balance in a single currency.
 *
 * <p>{@code balance} is a maintained cache of the account's ledger position. It exists so that
 * reading a balance is a single indexed row read rather than an aggregate over the whole ledger,
 * and it is only ever mutated inside the same database transaction that writes the corresponding
 * ledger entries. The ledger remains the authoritative record; see {@code docs/architecture.md}.
 */
@Entity
@Table(name = "bank_accounts")
@Getter
@Setter
public class BankAccount extends AuditedEntity {

    @Column(name = "account_number", nullable = false, length = 20)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType = AccountType.CUSTOMER;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private CurrencyCode currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20)
    private AccountPurpose purpose = AccountPurpose.SPENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = Money.zero();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected BankAccount() {
        // for JPA
    }

    public static BankAccount openCustomerAccount(User owner, String accountNumber, CurrencyCode currency) {
        return openCustomerAccount(owner, accountNumber, currency, AccountPurpose.SPENDING);
    }

    public static BankAccount openCustomerAccount(
            User owner, String accountNumber, CurrencyCode currency, AccountPurpose purpose) {
        BankAccount account = new BankAccount();
        account.owner = owner;
        account.accountNumber = accountNumber;
        account.currency = currency;
        account.accountType = AccountType.CUSTOMER;
        account.purpose = purpose;
        account.status = AccountStatus.ACTIVE;
        account.balance = Money.zero();
        return account;
    }

    public boolean isSavingsAccount() {
        return purpose == AccountPurpose.SAVINGS;
    }

    public boolean isOwnedBy(java.util.UUID userId) {
        return owner != null && owner.getId().equals(userId);
    }

    public boolean isSystemAccount() {
        return accountType == AccountType.SYSTEM;
    }

    /**
     * Removes money from this account.
     *
     * @throws BusinessRuleException if a customer account does not hold enough funds
     */
    public void debit(BigDecimal amount) {
        BigDecimal normalized = Money.normalize(amount);
        if (!accountType.allowsNegativeBalance() && !Money.isSufficient(balance, normalized)) {
            throw new BusinessRuleException(
                    ErrorCode.INSUFFICIENT_FUNDS,
                    "Account %s does not have sufficient funds for this transfer".formatted(accountNumber));
        }
        balance = Money.normalize(balance.subtract(normalized));
    }

    /** Adds money to this account. */
    public void credit(BigDecimal amount) {
        balance = Money.normalize(balance.add(Money.normalize(amount)));
    }

    /**
     * Asserts that this account may take part in a money movement right now.
     *
     * @throws BusinessRuleException if the account is frozen or closed
     */
    public void requireTransactable() {
        switch (status) {
            case ACTIVE -> {
                // usable
            }
            case FROZEN -> throw new BusinessRuleException(
                    ErrorCode.ACCOUNT_FROZEN, "Account %s is frozen".formatted(accountNumber));
            case CLOSED -> throw new BusinessRuleException(
                    ErrorCode.ACCOUNT_CLOSED, "Account %s is closed".formatted(accountNumber));
            default -> throw new BusinessRuleException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }
    }
}
