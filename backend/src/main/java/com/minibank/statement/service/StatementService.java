package com.minibank.statement.service;

import com.minibank.account.domain.BankAccount;
import com.minibank.account.service.AccountService;
import com.minibank.common.money.Money;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.ledger.domain.EntryType;
import com.minibank.ledger.domain.LedgerEntry;
import com.minibank.ledger.repository.LedgerEntryRepository;
import com.minibank.statement.dto.StatementResponse;
import com.minibank.transfer.dto.TransactionResponse;
import com.minibank.transfer.repository.TransferRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account statements for a calendar month.
 *
 * <h2>Where the opening balance comes from</h2>
 *
 * <p>It is derived, not stored. Each ledger entry records the balance immediately after it was
 * applied, so the opening balance is simply {@code balanceAfter} of the entry before the period —
 * or, when there is none, the balance the account started life with, which is zero. There is no
 * month-end snapshot to write, nothing to rebuild if a job misses a month, and a reversal posted
 * later cannot leave a stored figure stale.
 *
 * <p>The consequence is that {@code openingBalance + totalIn − totalOut} equals
 * {@code closingBalance} by construction. A statement that does not add up is not a formatting bug
 * in this design; it is impossible.
 */
@Service
public class StatementService {

    private final AccountService accountService;
    private final LedgerEntryRepository ledgerEntries;
    private final TransferRepository transfers;

    public StatementService(
            AccountService accountService,
            LedgerEntryRepository ledgerEntries,
            TransferRepository transfers) {
        this.accountService = accountService;
        this.ledgerEntries = ledgerEntries;
        this.transfers = transfers;
    }

    /**
     * Builds one month of statement for an account the caller may see.
     *
     * @param zone the customer's timezone, so a statement month matches the month on their calendar
     */
    @Transactional(readOnly = true)
    public StatementResponse forMonth(UUID accountId, YearMonth month, ZoneId zone, AuthenticatedUser caller) {
        // Ownership is enforced here, by the same method every other account read uses. A statement
        // is one of the most sensitive reads in the product and gets no special path.
        BankAccount account = accountService.getAccessibleAccount(accountId, caller);

        Instant from = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();

        List<LedgerEntry> entries = ledgerEntries.findForAccountBetween(accountId, from, to);

        BigDecimal totalIn = Money.zero();
        BigDecimal totalOut = Money.zero();
        for (LedgerEntry entry : entries) {
            if (entry.getEntryType() == EntryType.CREDIT) {
                totalIn = totalIn.add(entry.getAmount());
            } else {
                totalOut = totalOut.add(entry.getAmount());
            }
        }

        // Walk back from the first entry in the period rather than forward from the beginning of
        // time: one row instead of the account's whole history.
        BigDecimal opening = entries.isEmpty()
                ? balanceAsOf(accountId, from)
                : reverse(entries.get(0));
        BigDecimal closing = entries.isEmpty()
                ? opening
                : entries.get(entries.size() - 1).getBalanceAfter();

        Set<UUID> viewed = Set.of(accountId);
        List<TransactionResponse> transactions = transfers
                .findSettledForAnalytics(viewed, account.getCurrency(), from, to)
                .stream()
                .map(transfer -> TransactionResponse.from(transfer, viewed))
                .toList();

        return new StatementResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getCurrency(),
                month.toString(),
                from,
                to,
                opening,
                totalIn,
                totalOut,
                closing,
                entries.size(),
                transactions);
    }

    /** The balance the first entry of the period was applied to: its result, undone. */
    private static BigDecimal reverse(LedgerEntry first) {
        return first.getEntryType() == EntryType.CREDIT
                ? first.getBalanceAfter().subtract(first.getAmount())
                : first.getBalanceAfter().add(first.getAmount());
    }

    /**
     * The balance at a moment, when the period itself contains no movement.
     *
     * <p>Reads the last entry before the period. An account with no history at all opens at zero,
     * which is what it did.
     */
    private BigDecimal balanceAsOf(UUID accountId, Instant moment) {
        return ledgerEntries
                .findLastBefore(accountId, moment)
                .map(LedgerEntry::getBalanceAfter)
                .orElse(Money.zero());
    }
}
