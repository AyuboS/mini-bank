package com.minibank.common.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Rate limiting rules, bound from {@code minibank.rate-limit.*}.
 *
 * <p>Rules are ordered; the first one whose path and method match is applied.
 */
@Validated
@ConfigurationProperties(prefix = "minibank.rate-limit")
public record RateLimitProperties(@DefaultValue("true") boolean enabled, @Valid List<Rule> rules) {

    public RateLimitProperties {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /**
     * @param name identifies the rule in logs and metrics
     * @param pathPattern Ant-style path pattern, for example {@code /api/v1/auth/login}
     * @param methods HTTP methods the rule applies to; empty means all
     * @param capacity requests permitted in a full bucket
     * @param refillPeriod time in which an exhausted bucket refills completely
     */
    public record Rule(
            @NotBlank String name,
            @NotBlank String pathPattern,
            @DefaultValue List<String> methods,
            @Min(1) int capacity,
            @NotNull Duration refillPeriod) {

        public Rule {
            methods = methods == null
                    ? List.of()
                    : methods.stream().map(m -> m.toUpperCase(java.util.Locale.ROOT)).toList();
        }

        public boolean matchesMethod(String method) {
            return methods.isEmpty() || methods.contains(method.toUpperCase(java.util.Locale.ROOT));
        }
    }
}
