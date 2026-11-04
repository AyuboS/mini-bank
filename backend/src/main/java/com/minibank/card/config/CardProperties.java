package com.minibank.card.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Card issuing and card secret configuration, bound from {@code minibank.cards.*}.
 *
 * @param encryptionKey base64 of a 32-byte key protecting stored card numbers. Left empty the key
 *     is derived from the JWT secret, which is already mandatory; that keeps a fresh checkout
 *     working without a second secret to configure, while allowing the two to be rotated
 *     independently in a real deployment.
 * @param maxPerUser how many live cards one customer may hold at once
 * @param revealsPerHour how many times card details may be revealed per hour, per customer
 */
@Validated
@ConfigurationProperties(prefix = "minibank.cards")
public record CardProperties(
        @DefaultValue("") String encryptionKey,
        @Min(1) @Max(50) @DefaultValue("8") int maxPerUser,
        @Min(1) @DefaultValue("10") int revealsPerHour) {}
