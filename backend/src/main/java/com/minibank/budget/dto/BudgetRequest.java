package com.minibank.budget.dto;

import com.minibank.common.money.CurrencyCode;
import com.minibank.transfer.domain.TransactionCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Set, or change, the monthly cap on one category. */
@Schema(name = "BudgetRequest")
public record BudgetRequest(
        @NotNull @Schema(example = "GROCERIES") TransactionCategory category,
        @NotNull @Positive @Digits(integer = 15, fraction = 2) @Schema(example = "300.00") BigDecimal amount,
        @NotNull @Schema(example = "EUR") CurrencyCode currency) {}
