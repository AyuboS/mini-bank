package com.minibank.verification.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Verification tuning, bound from {@code minibank.verification.*}.
 *
 * <p>Every number that governs how guessable a code is lives here rather than as a literal in the
 * service, so the security posture can be read — and tightened — in one place.
 */
@Validated
@ConfigurationProperties(prefix = "minibank.verification")
public record VerificationProperties(@Valid @NotNull Phone phone, @Valid @NotNull Email email) {

    /**
     * @param codeTtl how long a code stays valid. Short: it is only six digits.
     * @param maxAttempts wrong guesses tolerated before the code is burned. Five attempts against a
     *     five minute window leaves a one-in-two-hundred-thousand chance per code.
     * @param resendCooldown minimum gap between messages to the same destination, which is what
     *     stops the endpoint being used to bill somebody's phone or to flood it
     */
    public record Phone(
            @NotNull @DefaultValue("5m") Duration codeTtl,
            @Min(1) @DefaultValue("5") int maxAttempts,
            @NotNull @DefaultValue("60s") Duration resendCooldown) {}

    /**
     * @param tokenTtl how long a verification link works for
     * @param resendCooldown minimum gap between verification emails to the same address
     */
    public record Email(
            @NotNull @DefaultValue("24h") Duration tokenTtl,
            @NotNull @DefaultValue("60s") Duration resendCooldown) {}
}
