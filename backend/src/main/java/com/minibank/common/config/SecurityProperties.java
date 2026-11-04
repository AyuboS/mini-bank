package com.minibank.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Security tuning, bound from {@code minibank.security.*}.
 *
 * <p>Everything here is externalised: no secret, origin or timeout is compiled into the
 * application. The constraints are enforced at startup, so a deployment with a short JWT secret
 * fails fast rather than running with weak signing.
 */
@Validated
@ConfigurationProperties(prefix = "minibank.security")
public record SecurityProperties(@Valid @NotNull Jwt jwt, @Valid @NotNull Lockout lockout, @Valid @NotNull Cors cors) {

    /**
     * @param secret HMAC-SHA signing secret; 32 characters is the minimum for HS256
     * @param issuer value placed in and required from the {@code iss} claim
     * @param accessTokenTtl how long an access token stays valid; deliberately short
     * @param refreshTokenTtl how long a refresh token stays valid
     */
    public record Jwt(
            @NotBlank @Size(min = 32, message = "JWT secret must be at least 32 characters") String secret,
            @NotBlank @DefaultValue("mbank") String issuer,
            @NotNull @DefaultValue("15m") Duration accessTokenTtl,
            @NotNull @DefaultValue("7d") Duration refreshTokenTtl) {}

    /**
     * @param maxFailedAttempts consecutive failures before the account is temporarily locked
     * @param duration how long the lock lasts before it releases itself
     */
    public record Lockout(
            @Min(1) @DefaultValue("5") int maxFailedAttempts, @NotNull @DefaultValue("15m") Duration duration) {}

    /**
     * @param allowedOrigins exact origins permitted to call the API with credentials. A wildcard is
     *     intentionally not supported: {@code *} combined with credentials is rejected by browsers
     *     and would be unsafe here anyway.
     */
    public record Cors(@NotEmpty List<String> allowedOrigins) {}
}
