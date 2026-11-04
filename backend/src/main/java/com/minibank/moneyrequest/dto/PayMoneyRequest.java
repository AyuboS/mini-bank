package com.minibank.moneyrequest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * The payer's decision to settle a request.
 *
 * <p>Carries only which account to pay from. The amount is deliberately not a field: it is fixed by
 * the request, and accepting one here would create an endpoint where the payer's client decides how
 * much moves.
 */
@Schema(name = "PayMoneyRequest")
public record PayMoneyRequest(
        @NotNull @Schema(description = "The account of yours the money comes from") UUID fromAccountId) {}
