package com.minibank.moneyrequest.repository;

import com.minibank.moneyrequest.domain.MoneyRequest;
import com.minibank.moneyrequest.domain.MoneyRequestStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MoneyRequestRepository extends JpaRepository<MoneyRequest, UUID> {

    /** Requests this customer sent. */
    @Query(
            """
            SELECT r FROM MoneyRequest r
            JOIN FETCH r.requesterAccount
            JOIN FETCH r.requester
            WHERE r.requester.id = :userId
            ORDER BY r.createdAt DESC
            """)
    List<MoneyRequest> findOutgoing(@Param("userId") UUID userId);

    /**
     * Requests addressed to any of this customer's account numbers.
     *
     * <p>Matched on account number rather than on a payer id, because a request may be raised
     * against an account number before its holder is known to be a customer at all.
     */
    @Query(
            """
            SELECT r FROM MoneyRequest r
            JOIN FETCH r.requesterAccount
            JOIN FETCH r.requester
            WHERE r.payerAccountNumber IN :accountNumbers
            ORDER BY r.createdAt DESC
            """)
    List<MoneyRequest> findIncoming(@Param("accountNumbers") Collection<String> accountNumbers);

    @Query("SELECT r FROM MoneyRequest r JOIN FETCH r.requesterAccount JOIN FETCH r.requester WHERE r.id = :id")
    Optional<MoneyRequest> findFullyById(@Param("id") UUID id);

    long countByRequesterIdAndStatus(UUID requesterId, MoneyRequestStatus status);
}
