package com.minibank.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The clock is injected, so refill behaviour is asserted by advancing a counter rather than by
 * sleeping. A test that sleeps for a minute to prove a one-minute refill is a test nobody runs.
 */
class TokenBucketRateLimiterTest {

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    private AtomicLong nanos;
    private TokenBucketRateLimiter limiter;

    @BeforeEach
    void setUp() {
        nanos = new AtomicLong(0L);
        limiter = new TokenBucketRateLimiter(nanos::get);
    }

    private void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }

    @Test
    void allowsRequestsUpToCapacityThenRefuses() {
        for (int i = 1; i <= 5; i++) {
            assertThat(limiter.tryConsume("user", 5, ONE_MINUTE).allowed())
                    .as("request %d of 5", i)
                    .isTrue();
        }
        assertThat(limiter.tryConsume("user", 5, ONE_MINUTE).allowed()).isFalse();
    }

    @Test
    void reportsRemainingTokens() {
        assertThat(limiter.tryConsume("user", 5, ONE_MINUTE).remainingTokens()).isEqualTo(4);
        assertThat(limiter.tryConsume("user", 5, ONE_MINUTE).remainingTokens()).isEqualTo(3);
    }

    @Test
    void suggestsWhenToRetry() {
        for (int i = 0; i < 5; i++) {
            limiter.tryConsume("user", 5, ONE_MINUTE);
        }
        TokenBucketRateLimiter.Decision refused = limiter.tryConsume("user", 5, ONE_MINUTE);

        assertThat(refused.allowed()).isFalse();
        assertThat(refused.retryAfterSeconds()).isBetween(1L, 12L);
    }

    @Test
    void refillsGraduallyRatherThanInAWindowStep() {
        for (int i = 0; i < 5; i++) {
            limiter.tryConsume("user", 5, ONE_MINUTE);
        }
        assertThat(limiter.tryConsume("user", 5, ONE_MINUTE).allowed()).isFalse();

        // One fifth of the refill period returns exactly one token.
        advance(Duration.ofSeconds(12));
        assertThat(limiter.tryConsume("user", 5, ONE_MINUTE).allowed()).isTrue();
        assertThat(limiter.tryConsume("user", 5, ONE_MINUTE).allowed()).isFalse();
    }

    @Test
    void neverAccumulatesBeyondCapacity() {
        limiter.tryConsume("user", 5, ONE_MINUTE);
        advance(Duration.ofHours(1));

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryConsume("user", 5, ONE_MINUTE).allowed()).isTrue();
        }
        assertThat(limiter.tryConsume("user", 5, ONE_MINUTE).allowed()).isFalse();
    }

    @Test
    void keepsCallersIndependent() {
        for (int i = 0; i < 5; i++) {
            limiter.tryConsume("noisy", 5, ONE_MINUTE);
        }
        assertThat(limiter.tryConsume("noisy", 5, ONE_MINUTE).allowed()).isFalse();
        assertThat(limiter.tryConsume("quiet", 5, ONE_MINUTE).allowed()).isTrue();
    }

    @Test
    void evictsIdleBucketsToBoundMemory() {
        limiter.tryConsume("a", 5, ONE_MINUTE);
        limiter.tryConsume("b", 5, ONE_MINUTE);
        assertThat(limiter.trackedKeys()).isEqualTo(2);

        advance(Duration.ofMinutes(31));
        limiter.tryConsume("b", 5, ONE_MINUTE);

        assertThat(limiter.evictIdleBuckets(Duration.ofMinutes(30))).isEqualTo(1);
        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }
}
