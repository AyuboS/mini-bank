package com.minibank.beneficiary.repository;

import com.minibank.beneficiary.domain.Beneficiary;
import com.minibank.beneficiary.domain.BeneficiaryStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {

    @Query(
            """
            SELECT b FROM Beneficiary b
            WHERE b.owner.id = :ownerId AND b.status = com.minibank.beneficiary.domain.BeneficiaryStatus.ACTIVE
            ORDER BY b.favourite DESC, b.nickname
            """)
    List<Beneficiary> findActiveByOwner(@Param("ownerId") UUID ownerId);

    Optional<Beneficiary> findByOwnerIdAndBeneficiaryAccountNumberAndStatus(
            UUID ownerId, String beneficiaryAccountNumber, BeneficiaryStatus status);

    /** Risk input: was this payee added recently? */
    @Query(
            """
            SELECT COUNT(b) > 0 FROM Beneficiary b
            WHERE b.owner.id = :ownerId
              AND b.beneficiaryAccountNumber = :accountNumber
              AND b.createdAt > :since
            """)
    boolean existsRecentlyAddedForOwner(
            @Param("ownerId") UUID ownerId,
            @Param("accountNumber") String accountNumber,
            @Param("since") Instant since);
}
