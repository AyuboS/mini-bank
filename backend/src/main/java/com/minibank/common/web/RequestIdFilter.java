package com.minibank.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns a correlation id to every request and publishes it to the logging MDC.
 *
 * <p>A client-supplied {@code X-Request-Id} is honoured so a trace can span the frontend and the
 * API, but only after validation: the value ends up in log lines, and accepting arbitrary text
 * there would allow log forging (newlines injecting fake entries) or unbounded log growth.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9_.:-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = sanitize(request.getHeader(RequestContext.REQUEST_ID_HEADER));
        MDC.put(RequestContext.REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(RequestContext.REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(RequestContext.REQUEST_ID_MDC_KEY);
        }
    }

    private static String sanitize(String candidate) {
        if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
