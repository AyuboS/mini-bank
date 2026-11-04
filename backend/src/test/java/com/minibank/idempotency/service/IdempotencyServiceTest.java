package com.minibank.idempotency.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minibank.common.error.ApiException;
import com.minibank.common.error.ErrorCode;
import com.minibank.idempotency.domain.IdempotencyRecord;
import com.minibank.user.domain.User;
import com.minibank.user.domain.UserRole;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    private static final String KEY = "key-123";
    private static final String ENDPOINT = "POST /api/v1/transfers";

    @Mock
    private IdempotencyStore store;

    private IdempotencyService service;
    private ObjectMapper objectMapper;
    private UUID userId;

    /** Stands in for a request body; two instances with the same values must hash identically. */
    private record SampleRequest(String to, String amount) {}

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new IdempotencyService(store, objectMapper);
        userId = UUID.randomUUID();
    }

    private IdempotencyRecord existingRecord(String requestHash) {
        User user = User.create("john", "john@example.com", "h", "John", "Smith", UserRole.CUSTOMER);
        user.setId(userId);
        IdempotencyRecord record = IdempotencyRecord.reserve(KEY, user, ENDPOINT, requestHash);
        record.setId(UUID.randomUUID());
        return record;
    }

    private String hashOf(Object payload) {
        // Reserve captures the hash the service computed, which is the value under test.
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        when(store.reserve(anyString(), any(), anyString(), captor.capture())).thenReturn(UUID.randomUUID());
        service.begin(KEY, userId, ENDPOINT, payload);
        return captor.getValue();
    }

    @Test
    void aFreshKeyProceeds() {
        UUID reservationId = UUID.randomUUID();
        when(store.reserve(anyString(), any(), anyString(), anyString())).thenReturn(reservationId);

        var reservation = service.begin(KEY, userId, ENDPOINT, new SampleRequest("MB-1", "100"));

        assertThat(reservation)
                .isInstanceOf(IdempotencyService.Reservation.Proceed.class)
                .extracting(r -> ((IdempotencyService.Reservation.Proceed) r).reservationId())
                .isEqualTo(reservationId);
    }

    @Test
    void identicalRequestsHashIdentically() {
        String first = hashOf(new SampleRequest("MB-1", "100"));
        org.mockito.Mockito.reset(store);
        String second = hashOf(new SampleRequest("MB-1", "100"));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void differentRequestsHashDifferently() {
        String first = hashOf(new SampleRequest("MB-1", "100"));
        org.mockito.Mockito.reset(store);
        String second = hashOf(new SampleRequest("MB-1", "999"));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void aCompletedKeyReplaysTheStoredResponseInsteadOfExecutingAgain() {
        SampleRequest request = new SampleRequest("MB-1", "100");
        String hash = hashOf(request);
        org.mockito.Mockito.reset(store);

        UUID resourceId = UUID.randomUUID();
        IdempotencyRecord record = existingRecord(hash);
        record.complete(201, "{\"reference\":\"TRX-2026-AAAAAAAA\"}", resourceId);
        when(store.reserve(anyString(), any(), anyString(), anyString()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(store.find(userId, ENDPOINT, KEY)).thenReturn(Optional.of(record));

        var reservation = service.begin(KEY, userId, ENDPOINT, request);

        assertThat(reservation).isInstanceOf(IdempotencyService.Reservation.ReplaySuccess.class);
        var replay = (IdempotencyService.Reservation.ReplaySuccess) reservation;
        assertThat(replay.status()).isEqualTo(201);
        assertThat(replay.resourceId()).isEqualTo(resourceId);
    }

    @Test
    void aFailedKeyReplaysTheSameErrorRatherThanRetrying() {
        SampleRequest request = new SampleRequest("MB-1", "100");
        String hash = hashOf(request);
        org.mockito.Mockito.reset(store);

        IdempotencyRecord record = existingRecord(hash);
        record.fail(422, "{\"code\":\"INSUFFICIENT_FUNDS\",\"message\":\"not enough\"}");
        when(store.reserve(anyString(), any(), anyString(), anyString()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(store.find(userId, ENDPOINT, KEY)).thenReturn(Optional.of(record));

        var reservation = service.begin(KEY, userId, ENDPOINT, request);

        assertThat(reservation).isInstanceOf(IdempotencyService.Reservation.ReplayFailure.class);
        assertThat(((IdempotencyService.Reservation.ReplayFailure) reservation).code())
                .isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void aKeyReusedWithADifferentBodyIsRejected() {
        String hashOfOriginal = hashOf(new SampleRequest("MB-1", "100"));
        org.mockito.Mockito.reset(store);

        when(store.reserve(anyString(), any(), anyString(), anyString()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(store.find(userId, ENDPOINT, KEY)).thenReturn(Optional.of(existingRecord(hashOfOriginal)));

        // Same key, different amount: answering with the first result would answer a question the
        // client did not ask.
        assertThatThrownBy(() -> service.begin(KEY, userId, ENDPOINT, new SampleRequest("MB-1", "999")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
    }

    @Test
    void aKeyStillExecutingIsRejectedRatherThanGuessed() {
        SampleRequest request = new SampleRequest("MB-1", "100");
        String hash = hashOf(request);
        org.mockito.Mockito.reset(store);

        when(store.reserve(anyString(), any(), anyString(), anyString()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(store.find(userId, ENDPOINT, KEY)).thenReturn(Optional.of(existingRecord(hash)));

        assertThatThrownBy(() -> service.begin(KEY, userId, ENDPOINT, request))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
    }

    @Test
    void releasingAKeyDeletesItSoTheClientCanRetry() {
        UUID reservationId = UUID.randomUUID();

        service.release(reservationId);

        verify(store).release(reservationId);
        verify(store, never()).markFailed(any(), org.mockito.ArgumentMatchers.anyInt(), anyString());
    }

    @Test
    void aStoredResponseIsReadBackIntoItsOriginalType() {
        String body = "{\"to\":\"MB-1\",\"amount\":\"100\"}";

        assertThat(service.readResponse(body, SampleRequest.class)).isEqualTo(new SampleRequest("MB-1", "100"));
    }
}
