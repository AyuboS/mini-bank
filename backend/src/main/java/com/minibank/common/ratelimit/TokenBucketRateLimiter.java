package com.minibank.common.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * An in-memory token bucket, keyed by caller.
 *
 * <p>Tokens refill continuously rather than in discrete windows, which avoids the burst-at-the-
 * boundary weakness of fixed windows (where a caller can spend a full window's budget at 59s and
 * another at 61s).
 *
 * <p><strong>Scope:</strong> the state lives in this process. That is correct for a single-node
 * deployment such as the Docker Compose stack shipped with this project, and it is the honest
 * limitation of this implementation: behind a load balancer, each node would enforce the limit
 * separately. Moving to a shared counter (Redis {@code INCR} with expiry, or a Lua token bucket) is
 * the documented next step, and only this class would change.
 *
 * <p>The time source is injectable so that the refill behaviour can be tested deterministically
 * instead of with sleeps.
 */
public class TokenBucketRateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier nanoTimeSupplier;

    public TokenBucketRateLimiter() {
        this(System::nanoTime);
    }

    public TokenBucketRateLimiter(LongSupplier nanoTimeSupplier) {
        this.nanoTimeSupplier = nanoTimeSupplier;
    }

    /**
     * Attempts to spend one token for {@code key}.
     *
     * @param capacity maximum number of requests in a full bucket
     * @param refillPeriod time in which an empty bucket refills completely
     */
    public Decision tryConsume(String key, int capacity, Duration refillPeriod) {
        Bucket bucket = buckets.computeIfAbsent(key, unused -> new Bucket(capacity, nanoTimeSupplier.getAsLong()));
        return bucket.tryConsume(capacity, refillPeriod.toNanos(), nanoTimeSupplier.getAsLong());
    }

    /** Drops buckets that have not been touched recently, bounding memory use. */
    public int evictIdleBuckets(Duration idleFor) {
        long threshold = nanoTimeSupplier.getAsLong() - idleFor.toNanos();
        int before = buckets.size();
        buckets.entrySet().removeIf(entry -> entry.getValue().lastAccessNanos() - threshold < 0);
        return before - buckets.size();
    }

    public int trackedKeys() {
        return buckets.size();
    }

    /**
     * Forgets every bucket, restoring a full allowance for all callers.
     *
     * <p>Operationally this is the "stop throttling everyone" lever after a misconfiguration; it is
     * also what keeps one test's spent budget from leaking into the next.
     */
    public void reset() {
        buckets.clear();
    }

    /** Outcome of a rate limit check. */
    public record Decision(boolean allowed, long retryAfterSeconds, int remainingTokens) {}

    /**
     * A single caller's bucket. Access is synchronised on the bucket instance: contention is per
     * key, and the critical section is a handful of arithmetic operations.
     */
    private static final class Bucket {

        private double tokens;
        private long lastRefillNanos;

        private Bucket(int capacity, long nowNanos) {
            this.tokens = capacity;
            this.lastRefillNanos = nowNanos;
        }

        private synchronized Decision tryConsume(int capacity, long refillPeriodNanos, long nowNanos) {
            refill(capacity, refillPeriodNanos, nowNanos);
            if (tokens >= 1.0d) {
                tokens -= 1.0d;
                return new Decision(true, 0L, (int) Math.floor(tokens));
            }
            double tokensNeeded = 1.0d - tokens;
            double nanosPerToken = (double) refillPeriodNanos / capacity;
            long retryAfterSeconds = Math.max(1L, (long) Math.ceil(tokensNeeded * nanosPerToken / 1_000_000_000d));
            return new Decision(false, retryAfterSeconds, 0);
        }

        private void refill(int capacity, long refillPeriodNanos, long nowNanos) {
            long elapsed = nowNanos - lastRefillNanos;
            if (elapsed <= 0) {
                return;
            }
            double refilled = (double) elapsed / refillPeriodNanos * capacity;
            tokens = Math.min(capacity, tokens + refilled);
            lastRefillNanos = nowNanos;
        }

        private synchronized long lastAccessNanos() {
            return lastRefillNanos;
        }
    }
}
