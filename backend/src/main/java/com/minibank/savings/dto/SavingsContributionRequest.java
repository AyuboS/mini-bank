package com.minibank.savings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Money into or out of a goal.
 *
 * <p>The account is named explicitly rather than assumed. Somebody with a EUR and a USD account
 * must be able to say which one is funding the goal, and a server that picks for them will
 * eventually pick wrong.
 */
@Schema(name = "SavingsContributionRequest")
public record SavingsContributionRequest(
        @NotNull @Schema(description = "The spending account the money moves from, or back to")
                UUID accountId,
        @NotNull @Positive @Digits(integer = 15, fraction = 2) @Schema(example = "50.00")
                BigDecimal amount) {}
