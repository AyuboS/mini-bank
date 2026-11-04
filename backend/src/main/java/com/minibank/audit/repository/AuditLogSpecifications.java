package com.minibank.audit.repository;

import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.domain.AuditLog;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Composable predicates for audit log search. A null filter contributes no restriction. */
public final class AuditLogSpecifications {

    private AuditLogSpecifications() {}

    public static Specification<AuditLog> byUser(UUID userId) {
        return userId == null ? null : (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<AuditLog> byAction(AuditAction action) {
        return action == null ? null : (root, query, cb) -> cb.equal(root.get("action"), action);
    }

    public static Specification<AuditLog> createdFrom(Instant from) {
        return from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<AuditLog> createdTo(Instant to) {
        return to == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}
