package com.minibank.common.config;

import com.minibank.common.ratelimit.TokenBucketRateLimiter;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Infrastructure beans that are not tied to a single feature module. */
@Configuration
public class ApplicationConfig {

    /**
     * A single injectable clock. Services take this rather than calling {@code Instant.now()}
     * directly, which is what makes time-dependent behaviour (token expiry, lockout windows, risk
     * velocity rules) testable without sleeping.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public TokenBucketRateLimiter tokenBucketRateLimiter() {
        return new TokenBucketRateLimiter();
    }
}
