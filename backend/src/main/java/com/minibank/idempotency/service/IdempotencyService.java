package com.minibank.idempotency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minibank.common.error.ApiException;
import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
import com.minibank.idempotency.domain.IdempotencyRecord;
import com.minibank.idempotency.domain.IdempotencyStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Orchestrates idempotent request handling.
 *
 * <p>Deliberately not {@code @Transactional}: it coordinates several independent transactions
 * through {@link IdempotencyStore}, and wrapping it in one would defeat their independence.
 *
 * <p>The contract:
 *
 * <ul>
 *   <li>First request with a key: reserved, executed, outcome stored.
 *   <li>Replay with the same body after success: the stored response is returned, no second
 *       execution.
 *   <li>Replay with the same body after a business failure: the stored error is returned. The
 *       client's answer does not change just because they asked twice.
 *   <li>Replay with a <em>different</em> body: rejected. Returning the first result would answer a
 *       question the client did not ask.
 *   <li>Replay while the first is still running: rejected with 409, because the outcome is not
 *       known yet and guessing either way could duplicate a payment.
 *   <li>Unexpected infrastructure failure: the reservation is released so a retry is possible.
 * </ul>
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyStore store;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    /**
     * Claims the key for this request, or reports what happened the first time it was used.
     *
     * @param requestPayload the request body, hashed to detect a key reused with different content
     */
    public Reservation begin(String key, UUID userId, String endpoint, Object requestPayload) {
        String requestHash = hashRequest(requestPayload);
        try {
            return new Reservation.Proceed(store.reserve(key, userId, endpoint, requestHash));
        } catch (DataIntegrityViolationException ex) {
            // Somebody already holds this key: either an earlier request, or a concurrent one that
            // won the race to insert.
            return resolveExisting(key, userId, endpoint, requestHash);
        }
    }

    /** Records a successful outcome so that a replay can return it. */
    public void complete(UUID reservationId, int status, Object responseBody, UUID resourceId) {
        store.markCompleted(reservationId, status, writeJson(responseBody), resourceId);
    }

    /** Records a business failure so that a replay returns the same error. */
    public void fail(UUID reservationId, ErrorCode code, String message) {
        store.markFailed(reservationId, code.status().value(), writeJson(new StoredFailure(code.name(), message)));
    }

    /** Frees the key after an infrastructure failure, so the client may retry. */
    public void release(UUID reservationId) {
        store.release(reservationId);
    }

    /** Rebuilds a stored success response into its original type. */
    public <T> T readResponse(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (JsonProcessingException ex) {
            // Only reachable if the stored payload and the DTO have drifted apart across a
            // deployment. Failing loudly beats returning a half-populated object for a payment.
            throw new IllegalStateException("Stored idempotent response could not be read back", ex);
        }
    }

    private Reservation resolveExisting(String key, UUID userId, String endpoint, String requestHash) {
        Optional<IdempotencyRecord> existing = store.find(userId, endpoint, key);
        if (existing.isEmpty()) {
            // The row disappeared between the failed insert and this read, which means a concurrent
            // request released it. Treating it as in progress asks the client to retry.
            throw new ApiException(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
        }

        IdempotencyRecord record = existing.get();
        if (!record.matches(requestHash)) {
            log.warn("Idempotency key reused with a different payload on {} by user {}", endpoint, userId);
            throw new BusinessRuleException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }

        return switch (record.getStatus()) {
            case IN_PROGRESS -> throw new ApiException(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
            case COMPLETED -> {
                log.info("Replaying stored response for idempotency key on {} (user {})", endpoint, userId);
                yield new Reservation.ReplaySuccess(
                        record.getResponseStatus(), record.getResponseBody(), record.getResourceId());
            }
            case FAILED -> {
                StoredFailure failure = readFailure(record.getResponseBody());
                log.info("Replaying stored failure {} for idempotency key on {}", failure.code(), endpoint);
                yield new Reservation.ReplayFailure(failure.code(), failure.message());
            }
        };
    }

    private StoredFailure readFailure(String body) {
        try {
            return objectMapper.readValue(body, StoredFailure.class);
        } catch (JsonProcessingException ex) {
            return new StoredFailure(ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.defaultMessage());
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Idempotent response could not be serialised", ex);
        }
    }

    /**
     * SHA-256 over the serialised request.
     *
     * <p>Records serialise their components in declaration order, so the same request object always
     * produces the same bytes and therefore the same digest.
     */
    private String hashRequest(Object payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = writeJson(payload).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    /** The stored shape of a business failure. */
    public record StoredFailure(String code, String message) {}

    /** What {@link #begin} concluded about this key. */
    public sealed interface Reservation {

        /** The key is ours; execute the operation and report the outcome. */
        record Proceed(UUID reservationId) implements Reservation {}

        /** This key already succeeded; return the stored response instead of executing again. */
        record ReplaySuccess(int status, String body, UUID resourceId) implements Reservation {}

        /** This key already failed; return the same error instead of executing again. */
        record ReplayFailure(String code, String message) implements Reservation {}
    }

    /** Convenience for callers that only need to know whether a record is still running. */
    public static boolean isInProgress(IdempotencyStatus status) {
        return status == IdempotencyStatus.IN_PROGRESS;
    }
}
