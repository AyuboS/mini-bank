package com.minibank.admin.web;

import com.minibank.admin.dto.TransferDecisionRequest;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.common.security.SecurityUtils;
import com.minibank.common.web.PageResponse;
import com.minibank.common.web.SortWhitelist;
import com.minibank.transfer.domain.TransferStatus;
import com.minibank.transfer.dto.TransferResponse;
import com.minibank.transfer.service.TransferAdministrationService;
import com.minibank.transfer.service.TransferQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Transfer oversight: reviewing held transfers and reversing settled ones. */
@RestController
@RequestMapping("/api/v1/admin/transfers")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: transfers", description = "Transfer oversight. Requires the ADMIN role.")
public class AdminTransferController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "amount", "status", "completedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final TransferQueryService transferQueryService;
    private final TransferAdministrationService administrationService;

    public AdminTransferController(
            TransferQueryService transferQueryService, TransferAdministrationService administrationService) {
        this.transferQueryService = transferQueryService;
        this.administrationService = administrationService;
    }

    @GetMapping
    @Operation(summary = "Search all transfers", description = "Across every customer of the bank.")
    public ResponseEntity<PageResponse<TransferResponse>> search(
            @RequestParam(required = false) TransferStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @PageableDefault(size = 20) Pageable pageable) {

        var filter = TransferQueryService.TransferFilter.of(status, from, to, minAmount, maxAmount);
        var page = transferQueryService.searchAll(filter, SortWhitelist.sanitize(pageable, SORTABLE, DEFAULT_SORT));
        return ResponseEntity.ok(PageResponse.from(page, TransferResponse::from));
    }

    @PostMapping("/{id}/approve")
    @Operation(
            summary = "Approve a held transfer",
            description = "Settles a transfer the risk engine held in PENDING. "
                    + "Balances are re-checked under lock at this point, so an approval can still "
                    + "fail if the account no longer has the funds.")
    @ApiResponse(responseCode = "200", description = "Transfer settled")
    @ApiResponse(responseCode = "409", description = "The transfer is not PENDING")
    public ResponseEntity<TransferResponse> approve(@PathVariable UUID id) {
        AuthenticatedUser admin = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(administrationService.approve(id, admin));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a held transfer", description = "Marks it FAILED. No money moves.")
    public ResponseEntity<TransferResponse> reject(
            @PathVariable UUID id, @Valid @RequestBody TransferDecisionRequest request) {
        AuthenticatedUser admin = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(administrationService.reject(id, admin, request.reason()));
    }

    @PostMapping("/{id}/reverse")
    @Operation(
            summary = "Reverse a settled transfer",
            description = "Creates a compensating transfer with opposite ledger entries and marks the "
                    + "original REVERSED. The original is never deleted or edited.")
    @ApiResponse(responseCode = "200", description = "Reversal created")
    @ApiResponse(responseCode = "409", description = "The transfer is not COMPLETED, or was already reversed")
    public ResponseEntity<TransferResponse> reverse(
            @PathVariable UUID id, @Valid @RequestBody TransferDecisionRequest request) {
        AuthenticatedUser admin = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(administrationService.reverse(id, admin, request.reason()));
    }
}
