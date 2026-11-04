package com.minibank.audit.service;

import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.domain.AuditLog;
import com.minibank.audit.repository.AuditLogRepository;
import com.minibank.audit.repository.AuditLogSpecifications;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the audit trail. Exposed to administrators only. */
@Service
public class AuditQueryService {

    private final AuditLogRepository auditLogRepository;

    public AuditQueryService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(UUID userId, AuditAction action, Instant from, Instant to, Pageable pageable) {
        Specification<AuditLog> spec = Specification.allOf(
                AuditLogSpecifications.byUser(userId),
                AuditLogSpecifications.byAction(action),
                AuditLogSpecifications.createdFrom(from),
                AuditLogSpecifications.createdTo(to));
        return auditLogRepository.findAll(spec, pageable);
    }
}
