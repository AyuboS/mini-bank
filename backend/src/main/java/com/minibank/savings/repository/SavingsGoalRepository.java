package com.minibank.savings.repository;

import com.minibank.savings.domain.SavingsGoal;
import com.minibank.savings.domain.SavingsGoalStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, UUID> {

    /**
     * The owner's goals with their backing accounts already joined.
     *
     * <p>The join is not optional: progress is read from the account balance, so without it every
     * goal in the list would trigger a second query for the account it is measured against.
     */
    @Query(
            """
            SELECT g FROM SavingsGoal g
            JOIN FETCH g.account
            WHERE g.owner.id = :ownerId
            ORDER BY g.status, g.createdAt
            """)
    List<SavingsGoal> findAllOwnedBy(@Param("ownerId") UUID ownerId);

    @Query("SELECT g FROM SavingsGoal g JOIN FETCH g.account WHERE g.id = :id")
    Optional<SavingsGoal> findWithAccountById(@Param("id") UUID id);

    long countByOwnerIdAndStatusNot(UUID ownerId, SavingsGoalStatus status);

    Optional<SavingsGoal> findByAccountId(UUID accountId);
}
