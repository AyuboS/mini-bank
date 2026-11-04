package com.minibank.beneficiary.web;

import com.minibank.beneficiary.dto.BeneficiaryRequest;
import com.minibank.beneficiary.dto.BeneficiaryResponse;
import com.minibank.beneficiary.service.BeneficiaryService;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Saved payees belonging to the authenticated customer. */
@RestController
@RequestMapping("/api/v1/beneficiaries")
@Tag(name = "Beneficiaries", description = "Saved payees")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    public BeneficiaryController(BeneficiaryService beneficiaryService) {
        this.beneficiaryService = beneficiaryService;
    }

    @PostMapping
    @Operation(summary = "Save a payee", description = "The account number must exist.")
    @ApiResponse(responseCode = "201", description = "Payee saved")
    @ApiResponse(responseCode = "409", description = "This payee is already saved")
    public ResponseEntity<BeneficiaryResponse> create(@Valid @RequestBody BeneficiaryRequest request) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(beneficiaryService.create(caller, request));
    }

    @GetMapping
    @Operation(summary = "List my saved payees")
    public ResponseEntity<List<BeneficiaryResponse>> list() {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(beneficiaryService.list(caller.getId()));
    }

    @PatchMapping("/{id}/favourite")
    @Operation(summary = "Pin or unpin a payee", description = "Favourites sort to the top of the list.")
    public ResponseEntity<BeneficiaryResponse> toggleFavourite(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(beneficiaryService.toggleFavourite(id, caller));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Remove a saved payee",
            description = "Soft delete: the relationship is retained for audit and risk purposes.")
    @ApiResponse(responseCode = "204", description = "Payee removed")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        beneficiaryService.delete(id, caller);
        return ResponseEntity.noContent().build();
    }
}
