package com.minibank.limit.repository;

import com.minibank.limit.domain.TransferLimitOverride;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferLimitOverrideRepository extends JpaRepository<TransferLimitOverride, UUID> {

    Optional<TransferLimitOverride> findByUserId(UUID userId);
}
