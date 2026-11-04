package com.minibank.audit.service;

import com.minibank.audit.domain.AuditLog;
import com.minibank.audit.repository.AuditLogRepository;
import com.minibank.common.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes the audit trail.
 *
 * <p>Two propagation modes are offered on purpose, because "always join" and "always separate" are
 * both wrong for a bank:
 *
 * <ul>
 *   <li>{@link #record} joins the caller's transaction. Use it for events that describe a committed
 *       change — a completed transfer's audit row must not survive if the transfer rolls back.
 *   <li>{@link #recordIndependently} commits in its own transaction. Use it for events that must
 *       survive a rollback — a failed login or a rejected transfer is precisely the case where the
 *       surrounding transaction is about to be rolled back, and losing that record would blind the
 *       security trail exactly when it matters.
 * </ul>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_USER_AGENT_LENGTH = 255;

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /** Records an event as part of the caller's transaction. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditEvent event) {
        persist(event);
    }

    /** Records an event in its own transaction, so it survives a rollback of the caller's. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(AuditEvent event) {
        persist(event);
    }

    private void persist(AuditEvent event) {
        AuditLog entry = AuditLog.of(event.action(), event.outcome());
        entry.setUserId(event.userId());
        entry.setUsername(event.username());
        entry.setEntityType(event.entityType());
        entry.setEntityId(event.entityId());
        entry.setMetadata(event.metadata().isEmpty() ? null : event.metadata());
        entry.setRequestId(RequestContext.currentRequestId());

        currentRequest().ifPresent(request -> {
            entry.setIpAddress(request.getRemoteAddr());
            entry.setUserAgent(truncate(request.getHeader("User-Agent")));
        });

        auditLogRepository.save(entry);
        log.info(
                "audit action={} outcome={} user={} entity={}:{}",
                event.action(),
                event.outcome(),
                event.username() == null ? "anonymous" : event.username(),
                event.entityType(),
                event.entityId());
    }

    private static java.util.Optional<HttpServletRequest> currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? java.util.Optional.of(attributes.getRequest())
                : java.util.Optional.empty();
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_USER_AGENT_LENGTH ? value : value.substring(0, MAX_USER_AGENT_LENGTH);
    }
}
