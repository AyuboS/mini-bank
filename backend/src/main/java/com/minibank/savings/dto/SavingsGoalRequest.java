package com.minibank.savings.dto;

import com.minibank.common.money.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** What it takes to open a savings goal. MBank opens the backing account itself. */
@Schema(name = "SavingsGoalRequest")
public record SavingsGoalRequest(
        @NotBlank @Size(max = 60) @Schema(example = "Thailand trip") String name,
        @NotNull
                @Positive
                @Digits(integer = 15, fraction = 2)
                @Schema(example = "1500.00")
                BigDecimal targetAmount,
        @NotNull @Schema(example = "EUR") CurrencyCode currency,
        @Schema(description = "Optional. Saving without a date is a legitimate way to save.")
                LocalDate deadline,
        @Pattern(
                        regexp = "^(violet|cyan|emerald|amber|coral|blue)$",
                        message = "must be one of the wallet's accent colours")
                @Schema(example = "violet")
                String accent) {}
