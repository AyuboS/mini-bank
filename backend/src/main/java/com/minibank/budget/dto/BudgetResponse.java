package com.minibank.budget.dto;

import com.minibank.common.money.CurrencyCode;
import com.minibank.transfer.domain.TransactionCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A budget and how it is doing this month.
 *
 * <p>{@code spent} is computed at read time from the customer's settled transactions, never stored.
 */
@Schema(name = "BudgetResponse")
public record BudgetResponse(
        UUID id,
        TransactionCategory category,
        CurrencyCode currency,
        @Schema(example = "300.0000") BigDecimal amount,
        @Schema(example = "180.0000") BigDecimal spent,
        @Schema(description = "Never negative: once the cap is passed there is nothing left, not a debt")
                BigDecimal remaining,
        @Schema(example = "60", description = "0–100, capped. usedPercentRaw carries the uncapped figure.")
                int usedPercent,
        @Schema(example = "60", description = "Uncapped, so an overspend is visible as e.g. 140")
                int usedPercentRaw,
        @Schema(description = "OK, NEARING when at or past 80%, OVER once the cap is passed")
                String state) {}
