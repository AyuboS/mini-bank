package com.minibank.card.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

/**
 * Card-level spending caps.
 *
 * <p>An explicit null clears a limit, so the two flags distinguish "not mentioned" from "remove
 * it". A record cannot tell those apart on its own, which is why they are here rather than being
 * inferred from the values.
 */
@Schema(name = "UpdateCardLimitsRequest")
public record UpdateCardLimitsRequest(
        @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 4) BigDecimal dailyLimit,
        @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 4) BigDecimal monthlyLimit,
        @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 4) BigDecimal atmLimit,
        @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 4) BigDecimal onlineLimit,
        @Schema(description = "Set true together with a null dailyLimit to remove the daily cap")
                boolean clearDailyLimit,
        @Schema(description = "Set true together with a null monthlyLimit to remove the monthly cap")
                boolean clearMonthlyLimit,
        @Schema(description = "Set true together with a null atmLimit to remove the ATM cap")
                boolean clearAtmLimit,
        @Schema(description = "Set true together with a null onlineLimit to remove the online cap")
                boolean clearOnlineLimit) {}
