package com.minibank.admin.web;

import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.dto.AuditLogResponse;
import com.minibank.audit.service.AuditQueryService;
import com.minibank.common.web.PageResponse;
import com.minibank.common.web.SortWhitelist;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only access to the audit trail. There is no endpoint that writes or deletes audit records. */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: audit", description = "Security and business audit trail. Requires the ADMIN role.")
public class AdminAuditController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "action", "outcome");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final AuditQueryService auditQueryService;

    public AdminAuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    @Operation(summary = "Search audit logs", description = "All filters are optional and combine with AND.")
    public ResponseEntity<PageResponse<AuditLogResponse>> search(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 50) Pageable pageable) {

        var page = auditQueryService.search(
                userId, action, from, to, SortWhitelist.sanitize(pageable, SORTABLE, DEFAULT_SORT));
        return ResponseEntity.ok(PageResponse.from(page, AuditLogResponse::from));
    }
}
