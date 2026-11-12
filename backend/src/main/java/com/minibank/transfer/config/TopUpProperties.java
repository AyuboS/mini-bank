package com.minibank.transfer.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Caps on simulated top-ups, bound from {@code minibank.top-up.*}.
 *
 * <p>Both caps are in the account's own currency. The daily cap is per customer and per currency,
 * counted from midnight UTC.
 */
@Validated
@ConfigurationProperties(prefix = "minibank.top-up")
public record TopUpProperties(@NotNull @Positive BigDecimal perTopUpMax, @NotNull @Positive BigDecimal dailyMax) {}
