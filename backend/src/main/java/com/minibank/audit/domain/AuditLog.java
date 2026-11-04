package com.minibank.audit.domain;

import com.minibank.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One append-only audit record.
 *
 * <p>Marked {@link Immutable}: Hibernate will not issue an UPDATE for this entity, so "append only"
 * is enforced by the mapping rather than by convention. The user id is stored as a plain UUID and
 * the username is denormalised, because a failed login for a username that does not exist still has
 * to be recorded.
 */
@Entity
@Table(name = "audit_logs")
@Immutable
@Getter
@Setter
public class AuditLog extends BaseEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "username", length = 50)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private AuditAction action;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 10)
    private AuditOutcome outcome = AuditOutcome.SUCCESS;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    protected AuditLog() {
        // for JPA
    }

    public static AuditLog of(AuditAction action, AuditOutcome outcome) {
        AuditLog entry = new AuditLog();
        entry.action = action;
        entry.outcome = outcome;
        return entry;
    }
}
