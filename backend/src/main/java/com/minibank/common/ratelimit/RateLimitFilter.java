package com.minibank.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minibank.common.error.ApiError;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.security.SecurityUtils;
import com.minibank.common.web.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies the configured rate limit rules to abuse-prone endpoints.
 *
 * <p>Callers are identified by user id when authenticated and by client address otherwise, so that
 * one signed-in customer hammering the transfer endpoint cannot exhaust the budget of everybody
 * behind the same NAT.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final Duration IDLE_BUCKET_TTL = Duration.ofMinutes(30);

    private final RateLimitProperties properties;
    private final TokenBucketRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitFilter(
            RateLimitProperties properties, TokenBucketRateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!properties.enabled()) {
            chain.doFilter(request, response);
            return;
        }

        Optional<RateLimitProperties.Rule> rule = findRule(request);
        if (rule.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        RateLimitProperties.Rule matched = rule.get();
        String key = matched.name() + ':' + callerKey(request);
        TokenBucketRateLimiter.Decision decision =
                rateLimiter.tryConsume(key, matched.capacity(), matched.refillPeriod());

        if (decision.allowed()) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(matched.capacity()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remainingTokens()));
            chain.doFilter(request, response);
            return;
        }

        log.warn("Rate limit '{}' exceeded for {} {}", matched.name(), request.getMethod(), request.getRequestURI());
        writeTooManyRequests(request, response, decision);
    }

    private Optional<RateLimitProperties.Rule> findRule(HttpServletRequest request) {
        String path = request.getRequestURI();
        return properties.rules().stream()
                .filter(rule -> rule.matchesMethod(request.getMethod()))
                .filter(rule -> pathMatcher.match(rule.pathPattern(), path))
                .findFirst();
    }

    /**
     * Uses the socket address rather than {@code X-Forwarded-For}. A client can set that header
     * freely, so trusting it here would let an attacker mint a fresh bucket per request. A real
     * deployment terminates TLS at a proxy configured with {@code server.forward-headers-strategy}
     * so that the container performs the substitution from a trusted source.
     */
    private static String callerKey(HttpServletRequest request) {
        return SecurityUtils.currentUserId()
                .map(id -> "user:" + id)
                .orElseGet(() -> "ip:" + request.getRemoteAddr());
    }

    private void writeTooManyRequests(
            HttpServletRequest request, HttpServletResponse response, TokenBucketRateLimiter.Decision decision)
            throws IOException {
        ApiError body = ApiError.of(
                ErrorCode.RATE_LIMIT_EXCEEDED,
                ErrorCode.RATE_LIMIT_EXCEEDED.defaultMessage(),
                request.getRequestURI(),
                RequestContext.currentRequestId());
        response.setStatus(ErrorCode.RATE_LIMIT_EXCEEDED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    /** Keeps the bucket map from growing without bound on a long-running instance. */
    @Scheduled(fixedDelay = 10, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public void evictIdleBuckets() {
        int evicted = rateLimiter.evictIdleBuckets(IDLE_BUCKET_TTL);
        if (evicted > 0) {
            log.debug("Evicted {} idle rate limit buckets, {} remaining", evicted, rateLimiter.trackedKeys());
        }
    }
}
