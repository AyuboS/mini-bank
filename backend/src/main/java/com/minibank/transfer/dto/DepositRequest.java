package com.minibank.transfer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * An administrative deposit into a customer account.
 *
 * <p>Money is not conjured: the counterparty is the bank's system funding account for that currency,
 * so the deposit is an ordinary double-entry movement like any other.
 */
@Schema(name = "DepositRequest")
public record DepositRequest(
        @NotNull @Positive @Digits(integer = 15, fraction = 2) @Schema(example = "500.00") BigDecimal amount,
        @Size(max = 140) @Schema(example = "Salary") String description) {}
