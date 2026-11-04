package com.minibank.common.security;

import com.minibank.common.error.ApiException;
import com.minibank.common.web.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Populates the security context from a {@code Authorization: Bearer <jwt>} header.
 *
 * <p>A malformed or expired token is not rejected here. The filter simply leaves the context empty
 * and lets the entry point produce the standard {@link com.minibank.common.error.ApiError} body, so
 * that unauthenticated responses look identical no matter which check failed.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractBearerToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                AuthenticatedUser principal = jwtService.parseAccessToken(token);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                MDC.put(RequestContext.USER_ID_MDC_KEY, principal.getId().toString());
            } catch (ApiException ex) {
                // The token value itself is never logged.
                log.debug("Rejected bearer token: {}", ex.getErrorCode());
                SecurityContextHolder.clearContext();
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(RequestContext.USER_ID_MDC_KEY);
        }
    }

    private static String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String value = header.substring(BEARER_PREFIX.length()).trim();
        return value.isEmpty() ? null : value;
    }
}
