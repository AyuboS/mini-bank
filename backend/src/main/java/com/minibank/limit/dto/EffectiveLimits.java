package com.minibank.limit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** The limits actually in force for a customer, after defaults and overrides are combined. */
@Schema(name = "EffectiveLimits")
public record EffectiveLimits(
        @Schema(example = "5000.0000") BigDecimal perTransferMax,
        @Schema(example = "10000.0000") BigDecimal dailyMax,
        @Schema(example = "20") int maxTransfersPerHour,
        @Schema(description = "True when an administrator has customised these limits") boolean customised) {}
