package com.minibank.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minibank.common.error.ApiError;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Renders 401 and 403 responses in the same {@link ApiError} shape as every other error.
 *
 * <p>Without this, Spring Security would return an empty body for unauthenticated requests, forcing
 * clients to special-case two different error formats.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        write(request, response, ErrorCode.UNAUTHENTICATED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(request, response, ErrorCode.FORBIDDEN);
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        ApiError body = ApiError.of(
                code, code.defaultMessage(), request.getRequestURI(), RequestContext.currentRequestId());
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
