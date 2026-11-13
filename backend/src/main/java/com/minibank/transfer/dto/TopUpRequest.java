package com.minibank.transfer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** A simulated top-up of one of the caller's own accounts. No real money is involved. */
@Schema(name = "TopUpRequest")
public record TopUpRequest(
        @NotNull @Positive @Digits(integer = 15, fraction = 2) @Schema(example = "100.00") BigDecimal amount) {}
