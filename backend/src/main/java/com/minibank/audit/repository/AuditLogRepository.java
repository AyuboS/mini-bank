package com.minibank.audit.repository;

import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.domain.AuditLog;
import com.minibank.audit.domain.AuditOutcome;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    /** Risk input: recent failed authentications for a user. */
    long countByUserIdAndActionAndOutcomeAndCreatedAtAfter(
            UUID userId, AuditAction action, AuditOutcome outcome, Instant after);

    /**
     * The customer's own sign-in history.
     *
     * <p>Serves the security page directly from the audit trail rather than from a second table.
     * Two stores of the same fact eventually disagree, and when they do it is the security page
     * that a customer is relying on.
     */
    @Query(
            """
            SELECT a FROM AuditLog a
            WHERE a.userId = :userId AND a.action IN :actions
            ORDER BY a.createdAt DESC
            """)
    List<AuditLog> findAuthenticationHistory(
            @Param("userId") UUID userId, @Param("actions") Collection<AuditAction> actions, Pageable pageable);
}
