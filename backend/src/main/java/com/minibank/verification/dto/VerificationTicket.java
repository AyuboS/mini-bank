package com.minibank.verification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Proof that a destination was just verified.
 *
 * <p>Handed out when a registration code checks out and presented again when the account is
 * created. It exists so that "this number is verified" is something the caller can prove rather
 * than something the server infers from a row it cannot attribute to anybody.
 */
@Schema(name = "VerificationTicket")
public record VerificationTicket(
        @Schema(example = "+998901234567") String destination,
        @Schema(description = "Opaque; present it as verificationToken when registering") String token,
        Instant expiresAt) {

    @Override
    public String toString() {
        return "VerificationTicket[destination=" + destination + ", token=***]";
    }
}
