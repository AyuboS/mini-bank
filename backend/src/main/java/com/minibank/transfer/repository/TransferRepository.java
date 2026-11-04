package com.minibank.transfer.repository;

import com.minibank.transfer.domain.Transfer;
import com.minibank.transfer.domain.TransferStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

/**
 * Transfer persistence.
 *
 * <p>Filtering uses {@link JpaSpecificationExecutor} rather than a JPQL query full of
 * {@code :param IS NULL OR ...} branches. Besides being unreadable, that pattern emits SQL with
 * untyped null parameters that PostgreSQL cannot infer types for, and it forces the planner to
 * consider predicates the caller never asked for.
 */
public interface TransferRepository extends JpaRepository<Transfer, UUID>, JpaSpecificationExecutor<Transfer> {

    /**
     * Eagerly joins both accounts. Transfer listings always render the account numbers, so without
     * this every page of twenty rows would trigger forty extra queries.
     */
    @Override
    @EntityGraph(attributePaths = {"sourceAccount", "destinationAccount"})
    Page<Transfer> findAll(Specification<Transfer> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"sourceAccount", "destinationAccount", "initiatedBy"})
    Optional<Transfer> findWithAccountsById(UUID id);

    @EntityGraph(attributePaths = {"sourceAccount", "destinationAccount"})
    Optional<Transfer> findByReference(String reference);

    Optional<Transfer> findByIdempotencyKeyAndInitiatedById(String idempotencyKey, UUID userId);

    boolean existsByReversalOfId(UUID originalTransferId);

    /** Risk input: how many transfers this user has started inside a recent window. */
    long countByInitiatedByIdAndCreatedAtAfter(UUID userId, Instant after);

    /**
     * Limit input: how much a user has already committed today.
     *
     * <p>PENDING and PROCESSING transfers are counted alongside COMPLETED ones deliberately: money
     * that is awaiting review is money the customer has already asked to send, and excluding it
     * would let somebody exceed a daily limit by queueing transfers faster than they settle.
     */
    @Query(
            """
            SELECT COALESCE(SUM(t.amount), 0) FROM Transfer t
            WHERE t.initiatedBy.id = :userId
              AND t.createdAt >= :since
              AND t.status IN :statuses
            """)
    BigDecimal sumAmountByInitiatorSince(
            @Param("userId") UUID userId,
            @Param("since") Instant since,
            @Param("statuses") Collection<TransferStatus> statuses);

    /**
     * Card spending input: how much has left one account inside a window.
     *
     * <p>Keyed on the source account rather than on the initiator, because a card belongs to an
     * account: money moved out of that account is what a card-level cap is meant to constrain.
     */
    @Query(
            """
            SELECT COALESCE(SUM(t.amount), 0) FROM Transfer t
            WHERE t.sourceAccount.id = :accountId
              AND t.createdAt >= :since
              AND t.status IN :statuses
            """)
    BigDecimal sumOutgoingByAccountSince(
            @Param("accountId") UUID accountId,
            @Param("since") Instant since,
            @Param("statuses") Collection<TransferStatus> statuses);

    /**
     * Budget input: how much has left one account in one category inside a window.
     *
     * <p>A narrower form of {@link #sumOutgoingByAccountSince} rather than a filter applied
     * afterwards, so the database returns one number instead of a page of rows to add up.
     */
    @Query(
            """
            SELECT COALESCE(SUM(t.amount), 0) FROM Transfer t
            WHERE t.sourceAccount.id = :accountId
              AND t.createdAt >= :since
              AND t.status IN :statuses
              AND t.category = :category
            """)
    BigDecimal sumOutgoingByAccountSinceForCategory(
            @Param("accountId") UUID accountId,
            @Param("since") Instant since,
            @Param("statuses") Collection<TransferStatus> statuses,
            @Param("category") com.minibank.transfer.domain.TransactionCategory category);

    /** Risk input: the customer's typical transfer size over a recent period. */
    @Query(
            """
            SELECT AVG(t.amount) FROM Transfer t
            WHERE t.initiatedBy.id = :userId
              AND t.createdAt >= :since
              AND t.status = com.minibank.transfer.domain.TransferStatus.COMPLETED
            """)
    BigDecimal averageCompletedAmountSince(@Param("userId") UUID userId, @Param("since") Instant since);

    /**
     * Settled movements touching any of the given accounts inside a window.
     *
     * <p>Analytics aggregate this in Java rather than in SQL, deliberately. Grouping by day or month
     * in JPQL means reaching for {@code FUNCTION('date_trunc', ...)}, which ties the query to
     * PostgreSQL and to a fixed timezone; and the row count here is one customer's transactions over
     * one period — tens to hundreds — where the cost of aggregating in memory is not measurable.
     * Portability and a timezone the caller controls are worth more than a group-by.
     *
     * <p>Both directions come back in one query so that income, spending and internal movement can
     * be told apart by looking at which side of each row the customer's accounts are on.
     */
    @Query(
            """
            SELECT t FROM Transfer t
            WHERE (t.sourceAccount.id IN :accountIds OR t.destinationAccount.id IN :accountIds)
              AND t.status = com.minibank.transfer.domain.TransferStatus.COMPLETED
              AND t.currency = :currency
              AND t.completedAt >= :from
              AND t.completedAt < :to
            ORDER BY t.completedAt
            """)
    List<Transfer> findSettledForAnalytics(
            @Param("accountIds") Collection<UUID> accountIds,
            @Param("currency") com.minibank.common.money.CurrencyCode currency,
            @Param("from") Instant from,
            @Param("to") Instant to);

    long countByStatus(TransferStatus status);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transfer t WHERE t.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") TransferStatus status);
}
