package com.minibank.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** Uniform error body returned by every endpoint in the API. */
@Schema(name = "ApiError", description = "Uniform error representation returned by all endpoints")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        @Schema(example = "2026-08-20T18:00:00Z") Instant timestamp,
        @Schema(example = "400") int status,
        @Schema(example = "VALIDATION_ERROR") String code,
        @Schema(example = "Request validation failed") String message,
        @Schema(example = "/api/v1/transfers") String path,
        @Schema(description = "Correlation id, also returned in the X-Request-Id response header")
                String requestId,
        @Schema(description = "Field level details, present for validation failures")
                List<FieldViolation> errors) {

    /** A single field level validation failure. */
    @Schema(name = "FieldViolation")
    public record FieldViolation(
            @Schema(example = "amount") String field,
            @Schema(example = "must be greater than zero") String message) {}

    public static ApiError of(ErrorCode code, String message, String path, String requestId) {
        return new ApiError(
                Instant.now(), code.status().value(), code.name(), message, path, requestId, List.of());
    }

    public static ApiError of(
            ErrorCode code, String message, String path, String requestId, List<FieldViolation> errors) {
        return new ApiError(Instant.now(), code.status().value(), code.name(), message, path, requestId, errors);
    }
}
