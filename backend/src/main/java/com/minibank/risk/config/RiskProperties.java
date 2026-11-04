package com.minibank.risk.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Tuning for the risk rules, bound from {@code minibank.risk.*}.
 *
 * <p>Thresholds live in configuration rather than in the rules so that they can be adjusted per
 * environment — a demo wants rules that visibly fire, production would not.
 */
@Validated
@ConfigurationProperties(prefix = "minibank.risk")
public record RiskProperties(
        @Valid @NotNull LargeAmount largeAmount,
        @Valid @NotNull Velocity velocity,
        @Valid @NotNull UnusualAmount unusualAmount,
        @Valid @NotNull FailedLogins failedLogins,
        @Valid @NotNull NewBeneficiary newBeneficiary,
        @Valid @NotNull Thresholds thresholds) {

    /** Rule 1: a single transfer above an absolute threshold. */
    public record LargeAmount(@NotNull @Positive BigDecimal threshold, @Min(0) @DefaultValue("35") int score) {}

    /** Rule 2: too many transfers in a short period. */
    public record Velocity(
            @NotNull Duration window, @Min(1) int maxTransfers, @Min(0) @DefaultValue("30") int score) {}

    /**
     * Rule 3: a transfer far larger than the customer's own recent norm.
     *
     * @param minimumAmount below this the rule stays silent, so somebody whose history is a few
     *     one-euro transfers is not flagged for spending twenty
     */
    public record UnusualAmount(
            @NotNull Duration historyWindow,
            @NotNull @Positive BigDecimal multiplier,
            @NotNull @Positive BigDecimal minimumAmount,
            @Min(0) @DefaultValue("25") int score) {}

    /** Rule 4: recent failed authentications on the initiating account. */
    public record FailedLogins(
            @NotNull Duration window, @Min(1) int threshold, @Min(0) @DefaultValue("20") int score) {}

    /** Rule 5: a large payment to a payee added moments ago. */
    public record NewBeneficiary(
            @NotNull Duration window,
            @NotNull @Positive BigDecimal amountThreshold,
            @Min(0) @DefaultValue("30") int score) {}

    /**
     * @param review score at or above which a transfer is held for manual approval
     * @param block score at or above which a transfer is refused
     */
    public record Thresholds(@Min(1) @DefaultValue("50") int review, @Min(1) @DefaultValue("85") int block) {}
}
