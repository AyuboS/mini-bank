package com.minibank.auth.session;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One signed-in device.
 *
 * <p>Identified by its token family rather than by a token id: rotation replaces the token several
 * times a day, and a customer ending "this MacBook" means the login, not the current token.
 *
 * <p>No token, hash or fragment of either appears here. The id is a family UUID, which is useful
 * only for ending the session it names.
 */
@Schema(name = "SessionResponse")
public record SessionResponse(
        @Schema(description = "The token family. Pass this to end the session.") UUID id,
        @Schema(example = "Chrome on macOS") String device,
        @Schema(example = "192.0.2.14") String ipAddress,
        @Schema(description = "True for the session making this request") boolean current,
        Instant lastUsedAt,
        Instant createdAt,
        Instant expiresAt) {}
