package com.minibank.auth.repository;

import com.minibank.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Query("SELECT t FROM RefreshToken t JOIN FETCH t.user WHERE t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashWithUser(@Param("tokenHash") String tokenHash);

    List<RefreshToken> findAllByFamilyIdAndRevokedAtIsNull(UUID familyId);

    List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(UUID userId);

    /**
     * The user's live sessions, most recently used first.
     *
     * <p>Expired tokens are excluded in the query rather than filtered afterwards: a session that
     * can no longer be used is not a session, and showing one the customer cannot end is worse than
     * showing nothing.
     */
    @Query(
            """
            SELECT t FROM RefreshToken t
            WHERE t.user.id = :userId
              AND t.revokedAt IS NULL
              AND t.expiresAt > :now
            ORDER BY COALESCE(t.lastUsedAt, t.createdAt) DESC
            """)
    List<RefreshToken> findLiveSessions(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Every live token in the same family.
     *
     * <p>Ending a session means ending the family, not one token: rotation means a single login can
     * have a current token and, briefly, its predecessor.
     */
    @Query(
            """
            SELECT t FROM RefreshToken t
            WHERE t.user.id = :userId AND t.familyId = :familyId AND t.revokedAt IS NULL
            """)
    List<RefreshToken> findLiveInFamily(@Param("userId") UUID userId, @Param("familyId") UUID familyId);

    /** Housekeeping: expired tokens can never be used again, so they are safe to delete. */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff")
    int deleteAllExpiredBefore(@Param("cutoff") Instant cutoff);
}
