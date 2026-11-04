package com.minibank.ledger.service;

import com.minibank.account.domain.BankAccount;
import com.minibank.ledger.domain.EntryType;
import com.minibank.ledger.domain.LedgerEntry;
import com.minibank.ledger.repository.LedgerEntryRepository;
import com.minibank.transfer.domain.Transfer;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only component permitted to move money.
 *
 * <p>Mutating a balance and writing the matching ledger entries is a single operation here, which is
 * what keeps the two in step. No other class calls {@link BankAccount#debit} or {@link
 * BankAccount#credit}, so there is no code path that can change a balance without leaving an
 * accounting trail, and none that can post an entry without moving the balance.
 *
 * <p>Callers are responsible for having locked the accounts first; see {@code TransferExecutor}.
 */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    /**
     * Applies a balanced pair of postings: {@code amount} leaves {@code source} and the same
     * {@code amount} arrives in {@code destination}.
     *
     * <p>Must run inside the caller's transaction. If anything later in that transaction fails, both
     * the balance updates and the entries roll back together — money is never half-moved.
     *
     * @throws com.minibank.common.error.BusinessRuleException if the source lacks the funds
     */
    @Transactional
    public List<LedgerEntry> postDoubleEntry(
            Transfer transfer, BankAccount source, BankAccount destination, BigDecimal amount) {

        // Order matters: debiting first means an insufficient-funds failure aborts before anything
        // has been credited, so a rollback has less to undo and the intent stays obvious.
        source.debit(amount);
        destination.credit(amount);

        LedgerEntry debit = LedgerEntry.post(transfer, source, EntryType.DEBIT, amount);
        LedgerEntry credit = LedgerEntry.post(transfer, destination, EntryType.CREDIT, amount);
        List<LedgerEntry> entries = ledgerEntryRepository.saveAll(List.of(debit, credit));

        log.debug(
                "Posted ledger entries for transfer {}: DEBIT {} {} from {}, CREDIT to {}",
                transfer.getReference(),
                amount,
                source.getCurrency(),
                source.getAccountNumber(),
                destination.getAccountNumber());
        return entries;
    }

    /**
     * Recomputes an account's balance from its ledger history.
     *
     * <p>Used by reconciliation and by tests to assert that the cached balance has not drifted from
     * the authoritative record.
     */
    @Transactional(readOnly = true)
    public BigDecimal computeBalanceFromLedger(java.util.UUID accountId) {
        return ledgerEntryRepository.computeBalanceFromLedger(accountId);
    }

    /** True when a transfer's debits and credits cancel out exactly, as double entry requires. */
    @Transactional(readOnly = true)
    public boolean isTransferBalanced(java.util.UUID transferId) {
        BigDecimal debits = ledgerEntryRepository.sumByTransferAndType(transferId, EntryType.DEBIT);
        BigDecimal credits = ledgerEntryRepository.sumByTransferAndType(transferId, EntryType.CREDIT);
        return debits.compareTo(credits) == 0;
    }
}
