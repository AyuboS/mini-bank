package com.minibank.ledger.domain;

import com.minibank.account.domain.BankAccount;
import com.minibank.common.money.CurrencyCode;
import com.minibank.common.persistence.BaseEntity;
import com.minibank.transfer.domain.Transfer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

/**
 * One side of a double-entry posting.
 *
 * <p>Immutable by design and by mapping: a correction is made by writing compensating entries, never
 * by editing history. {@code balanceAfter} snapshots the account balance the moment this entry was
 * applied, which is what makes a statement reproducible without replaying the entire ledger.
 */
@Entity
@Table(name = "ledger_entries")
@Immutable
@Getter
public class LedgerEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false, updatable = false)
    private Transfer transfer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private BankAccount account;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false, length = 6)
    private EntryType entryType;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private CurrencyCode currency;

    @Column(name = "balance_after", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    protected LedgerEntry() {
        // for JPA
    }

    public static LedgerEntry post(
            Transfer transfer, BankAccount account, EntryType entryType, BigDecimal amount) {
        LedgerEntry entry = new LedgerEntry();
        entry.transfer = transfer;
        entry.account = account;
        entry.entryType = entryType;
        entry.amount = amount;
        entry.currency = account.getCurrency();
        entry.balanceAfter = account.getBalance();
        return entry;
    }

    /** This entry's signed effect on the account balance: positive for credits, negative for debits. */
    public BigDecimal signedAmount() {
        return entryType.signedAmount(amount);
    }
}
