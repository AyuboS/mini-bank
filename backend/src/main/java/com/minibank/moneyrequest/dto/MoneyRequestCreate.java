package com.minibank.moneyrequest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Ask somebody for money. */
@Schema(name = "MoneyRequestCreate")
public record MoneyRequestCreate(
        @NotNull @Schema(description = "The account of yours the money should land in") UUID toAccountId,
        @NotNull
                @Pattern(regexp = "^MB-[0-9]{12}$", message = "must be a valid MBank account number")
                @Schema(description = "The account being asked", example = "MB-482910375512")
                String fromAccountNumber,
        @NotNull @Positive @Digits(integer = 15, fraction = 2) @Schema(example = "25.00") BigDecimal amount,
        @Size(max = 140) @Schema(example = "Dinner on Friday") String description,
        @Min(1)
                @Max(30)
                @Schema(
                        description = "Days before the request lapses. Defaults to 7.",
                        example = "7")
                Integer expiresInDays) {}
