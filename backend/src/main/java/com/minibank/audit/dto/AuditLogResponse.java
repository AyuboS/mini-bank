package com.minibank.audit.dto;

import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.domain.AuditLog;
import com.minibank.audit.domain.AuditOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** An audit record as returned to administrators. */
@Schema(name = "AuditLogResponse")
public record AuditLogResponse(
        UUID id,
        UUID userId,
        String username,
        AuditAction action,
        AuditOutcome outcome,
        String entityType,
        String entityId,
        String ipAddress,
        String requestId,
        Map<String, Object> metadata,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog entry) {
        return new AuditLogResponse(
                entry.getId(),
                entry.getUserId(),
                entry.getUsername(),
                entry.getAction(),
                entry.getOutcome(),
                entry.getEntityType(),
                entry.getEntityId(),
                entry.getIpAddress(),
                entry.getRequestId(),
                entry.getMetadata(),
                entry.getCreatedAt());
    }
}
