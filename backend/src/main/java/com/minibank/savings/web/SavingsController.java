package com.minibank.savings.web;

import com.minibank.common.security.AuthenticatedUser;
import com.minibank.common.security.SecurityUtils;
import com.minibank.savings.dto.SavingsContributionRequest;
import com.minibank.savings.dto.SavingsGoalRequest;
import com.minibank.savings.dto.SavingsGoalResponse;
import com.minibank.savings.service.SavingsService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Savings goals.
 *
 * <p>Contributions and withdrawals move real money through the ledger, so the two endpoints that do
 * so are POSTs against a goal rather than PATCHes of a balance — there is no balance field to set.
 */
@RestController
@RequestMapping("/api/v1/savings/goals")
@Tag(name = "Savings", description = "Named goals, each backed by a real account")
public class SavingsController {

    private final SavingsService savingsService;

    public SavingsController(SavingsService savingsService) {
        this.savingsService = savingsService;
    }

    @GetMapping
    @Operation(summary = "My savings goals")
    public ResponseEntity<List<SavingsGoalResponse>> list() {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(
                savingsService.list(caller.getId()).stream().map(SavingsGoalResponse::from).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "One goal")
    public ResponseEntity<SavingsGoalResponse> get(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(SavingsGoalResponse.from(savingsService.get(id, caller)));
    }

    @PostMapping
    @Operation(
            summary = "Open a savings goal",
            description = "MBank opens a dedicated account for the goal in the same currency.")
    @ApiResponse(responseCode = "201", description = "Goal opened")
    public ResponseEntity<SavingsGoalResponse> create(@Valid @RequestBody SavingsGoalRequest request) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SavingsGoalResponse.from(savingsService.create(request, caller)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename a goal, or move its target or deadline")
    public ResponseEntity<SavingsGoalResponse> update(
            @PathVariable UUID id, @Valid @RequestBody SavingsGoalRequest request) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(SavingsGoalResponse.from(savingsService.update(id, request, caller)));
    }

    @PostMapping("/{id}/contributions")
    @Operation(
            summary = "Put money into the goal",
            description = "A settled internal transfer from the account you name into the goal's account.")
    @ApiResponse(responseCode = "422", description = "The funding account cannot cover the amount")
    public ResponseEntity<SavingsGoalResponse> contribute(
            @PathVariable UUID id, @Valid @RequestBody SavingsContributionRequest request) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(SavingsGoalResponse.from(savingsService.contribute(id, request, caller)));
    }

    @PostMapping("/{id}/withdrawals")
    @Operation(summary = "Take money back out of the goal")
    public ResponseEntity<SavingsGoalResponse> withdraw(
            @PathVariable UUID id, @Valid @RequestBody SavingsContributionRequest request) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(SavingsGoalResponse.from(savingsService.withdraw(id, request, caller)));
    }

    @PatchMapping("/{id}/archive")
    @Operation(summary = "Archive a goal", description = "Only once it is empty.")
    @ApiResponse(responseCode = "409", description = "The goal still holds money")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        savingsService.archive(id, caller);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Archive a goal", description = "Same as PATCH /archive; goals are never hard-deleted.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        return archive(id);
    }
}
