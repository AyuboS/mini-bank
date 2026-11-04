package com.minibank.verification.repository;

import com.minibank.verification.domain.VerificationChannel;
import com.minibank.verification.domain.VerificationCode;
import com.minibank.verification.domain.VerificationPurpose;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, UUID> {

    /**
     * The verification in progress for a destination and purpose.
     *
     * <p>At most one exists: the unique constraint on (channel, destination, purpose) means a
     * resend updates the existing row rather than piling up a history of live codes, each of which
     * would be independently guessable.
     */
    Optional<VerificationCode> findByChannelAndDestinationAndPurpose(
            VerificationChannel channel, String destination, VerificationPurpose purpose);

    Optional<VerificationCode> findByChannelAndDestinationAndPurposeAndTicketHash(
            VerificationChannel channel, String destination, VerificationPurpose purpose, String ticketHash);

    /** Email tokens arrive back as a bare token, with no destination to look them up by. */
    Optional<VerificationCode> findByChannelAndSecretHash(VerificationChannel channel, String secretHash);

    /**
     * Clears out rows that can no longer be used for anything.
     *
     * <p>Verification rows are the highest-churn table in the application and every one of them
     * holds a contact detail. Keeping spent ones forever would turn a transient record into a
     * standing collection of phone numbers.
     */
    @Modifying
    @Query(
            """
            DELETE FROM VerificationCode v
            WHERE v.expiresAt < :cutoff
              AND (v.ticketExpiresAt IS NULL OR v.ticketExpiresAt < :cutoff)
            """)
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
