package com.minibank.ledger.repository;

import com.minibank.ledger.domain.EntryType;
import com.minibank.ledger.domain.LedgerEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    @Query("SELECT e FROM LedgerEntry e WHERE e.transfer.id = :transferId ORDER BY e.entryType")
    List<LedgerEntry> findByTransferId(@Param("transferId") UUID transferId);

    @Query("SELECT e FROM LedgerEntry e WHERE e.account.id = :accountId ORDER BY e.createdAt DESC")
    Page<LedgerEntry> findByAccountId(@Param("accountId") UUID accountId, Pageable pageable);

    /**
     * The account's balance recomputed from the ledger.
     *
     * <p>This is the value {@code bank_accounts.balance} is supposed to cache. It is used by the
     * reconciliation check rather than on the read path, because aggregating an account's whole
     * history on every balance enquiry would not scale.
     */
    @Query(
            """
            SELECT COALESCE(SUM(CASE WHEN e.entryType = com.minibank.ledger.domain.EntryType.CREDIT
                                     THEN e.amount ELSE -e.amount END), 0)
            FROM LedgerEntry e
            WHERE e.account.id = :accountId
            """)
    BigDecimal computeBalanceFromLedger(@Param("accountId") UUID accountId);

    @Query(
            """
            SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e
            WHERE e.transfer.id = :transferId AND e.entryType = :entryType
            """)
    BigDecimal sumByTransferAndType(
            @Param("transferId") UUID transferId, @Param("entryType") EntryType entryType);

    /**
     * The account's postings inside a window, oldest first.
     *
     * <p>Ordered ascending because a statement is read forwards, and because the first row is what
     * the opening balance is derived from.
     */
    @Query(
            """
            SELECT e FROM LedgerEntry e
            WHERE e.account.id = :accountId AND e.createdAt >= :from AND e.createdAt < :to
            ORDER BY e.createdAt, e.id
            """)
    List<LedgerEntry> findForAccountBetween(
            @Param("accountId") UUID accountId, @Param("from") Instant from, @Param("to") Instant to);

    /**
     * The last posting before a moment.
     *
     * <p>Serves the opening balance of a period that contains no movement at all. Paged to one row
     * rather than fetching the account's history and taking the last of it.
     */
    @Query(
            """
            SELECT e FROM LedgerEntry e
            WHERE e.account.id = :accountId AND e.createdAt < :moment
            ORDER BY e.createdAt DESC, e.id DESC
            LIMIT 1
            """)
    Optional<LedgerEntry> findLastBefore(@Param("accountId") UUID accountId, @Param("moment") Instant moment);

    long countByTransferId(UUID transferId);
}
