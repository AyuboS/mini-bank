package com.minibank.common.web;

import org.slf4j.MDC;

/**
 * Access to per-request correlation data.
 *
 * <p>The correlation id is stored in the SLF4J {@link MDC} by {@link RequestIdFilter} so that every
 * log line emitted while handling a request carries it, and is echoed back to the client in the
 * {@code X-Request-Id} response header.
 */
public final class RequestContext {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    public static final String USER_ID_MDC_KEY = "userId";

    private RequestContext() {}

    public static String currentRequestId() {
        String requestId = MDC.get(REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }
}
