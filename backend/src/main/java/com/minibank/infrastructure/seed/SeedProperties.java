package com.minibank.infrastructure.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Controls demo data seeding, bound from {@code minibank.seed.*}.
 *
 * @param enabled whether to create the demo users described in the README. Always false under the
 *     prod profile.
 */
@ConfigurationProperties(prefix = "minibank.seed")
public record SeedProperties(@DefaultValue("false") boolean enabled) {}
