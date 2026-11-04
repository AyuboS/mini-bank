package com.minibank.scheduling.repository;

import com.minibank.scheduling.domain.ScheduleStatus;
import com.minibank.scheduling.domain.ScheduledTransfer;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduledTransferRepository extends JpaRepository<ScheduledTransfer, UUID> {

    @Query(
            """
            SELECT s FROM ScheduledTransfer s
            JOIN FETCH s.sourceAccount
            WHERE s.owner.id = :ownerId
            ORDER BY s.status, s.nextRunOn
            """)
    List<ScheduledTransfer> findAllOwnedBy(@Param("ownerId") UUID ownerId);

    @Query("SELECT s FROM ScheduledTransfer s JOIN FETCH s.sourceAccount JOIN FETCH s.owner WHERE s.id = :id")
    Optional<ScheduledTransfer> findWithAccountById(@Param("id") UUID id);

    /**
     * Everything the runner should attempt on this date.
     *
     * <p>Paged, so one enormous batch cannot hold a transaction open across thousands of payments.
     * Ordered by id rather than by date so the ordering is total and stable: two orders due on the
     * same day must not be able to swap places between pages.
     */
    @Query(
            """
            SELECT s.id FROM ScheduledTransfer s
            WHERE s.status = com.minibank.scheduling.domain.ScheduleStatus.SCHEDULED
              AND s.nextRunOn <= :today
            ORDER BY s.id
            """)
    List<UUID> findDueIds(@Param("today") LocalDate today, Pageable pageable);

    long countByOwnerIdAndStatus(UUID ownerId, ScheduleStatus status);
}
