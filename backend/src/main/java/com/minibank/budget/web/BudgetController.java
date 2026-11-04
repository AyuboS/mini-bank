package com.minibank.budget.web;

import com.minibank.budget.dto.BudgetRequest;
import com.minibank.budget.dto.BudgetResponse;
import com.minibank.budget.service.BudgetService;
import com.minibank.common.money.CurrencyCode;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Monthly caps per spending category. */
@RestController
@RequestMapping("/api/v1/budgets")
@Tag(name = "Budgets", description = "Monthly spending caps, measured against real transactions")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    @Operation(summary = "My budgets, with this month's progress against each")
    public ResponseEntity<List<BudgetResponse>> list(
            @RequestParam(defaultValue = "EUR") CurrencyCode currency,
            @RequestParam(required = false) String timezone) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(budgetService.listWithProgress(caller.getId(), currency, zoneOf(timezone)));
    }

    @PutMapping
    @Operation(
            summary = "Set the cap for a category",
            description = "Creates the budget if there is none, otherwise moves the existing one.")
    public ResponseEntity<BudgetResponse> set(
            @Valid @RequestBody BudgetRequest request, @RequestParam(required = false) String timezone) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(budgetService.set(request, caller, zoneOf(timezone)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a budget")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        budgetService.delete(id, caller);
        return ResponseEntity.noContent().build();
    }

    private static ZoneId zoneOf(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (RuntimeException ignored) {
            return ZoneId.of("UTC");
        }
    }
}
