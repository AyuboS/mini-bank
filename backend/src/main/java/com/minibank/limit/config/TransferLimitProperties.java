package com.minibank.limit.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Platform default transfer limits, bound from {@code minibank.limits.*}.
 *
 * <p>These apply to every customer unless a {@link com.minibank.limit.domain.TransferLimitOverride}
 * exists for them, so the common case requires no rows at all.
 */
@Validated
@ConfigurationProperties(prefix = "minibank.limits")
public record TransferLimitProperties(
        @NotNull @Positive BigDecimal perTransferMax,
        @NotNull @Positive BigDecimal dailyMax,
        @Min(1) int maxTransfersPerHour) {}
