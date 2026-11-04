package com.minibank.audit.service;

import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.domain.AuditOutcome;
import com.minibank.user.domain.User;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Describes something worth auditing.
 *
 * <p>An immutable value with a fluent wither API, so call sites read as a sentence and cannot
 * accidentally leave a required field unset.
 */
public record AuditEvent(
        AuditAction action,
        AuditOutcome outcome,
        UUID userId,
        String username,
        String entityType,
        String entityId,
        Map<String, Object> metadata) {

    public AuditEvent {
        // Not Map.copyOf: that rejects null values, and metadata routinely describes optional
        // fields. An unmodifiable LinkedHashMap keeps insertion order and tolerates them.
        metadata = metadata == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public static AuditEvent success(AuditAction action) {
        return new AuditEvent(action, AuditOutcome.SUCCESS, null, null, null, null, Map.of());
    }

    public static AuditEvent failure(AuditAction action) {
        return new AuditEvent(action, AuditOutcome.FAILURE, null, null, null, null, Map.of());
    }

    public AuditEvent by(User user) {
        return new AuditEvent(action, outcome, user.getId(), user.getUsername(), entityType, entityId, metadata);
    }

    public AuditEvent by(UUID id, String name) {
        return new AuditEvent(action, outcome, id, name, entityType, entityId, metadata);
    }

    public AuditEvent on(String type, Object id) {
        return new AuditEvent(
                action, outcome, userId, username, type, id == null ? null : id.toString(), metadata);
    }

    /**
     * Adds a metadata entry.
     *
     * <p>A null value adds nothing. Callers pass optional fields here directly — an administrator
     * may update one transfer limit and leave the others unset — and for an audit record "the key
     * is absent" and "the key is null" mean the same thing, so the entry is simply omitted.
     */
    public AuditEvent with(String key, Object value) {
        if (value == null) {
            return this;
        }
        Map<String, Object> merged = new LinkedHashMap<>(metadata);
        merged.put(key, value.toString());
        return new AuditEvent(action, outcome, userId, username, entityType, entityId, merged);
    }
}
