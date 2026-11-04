package com.minibank.idempotency.repository;

import com.minibank.idempotency.domain.IdempotencyRecord;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByUserIdAndEndpointAndIdempotencyKey(
            UUID userId, String endpoint, String idempotencyKey);

    /** Housekeeping: keys are only meaningful for as long as a client might retry. */
    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.createdAt < :cutoff")
    int deleteAllCreatedBefore(@Param("cutoff") Instant cutoff);
}
