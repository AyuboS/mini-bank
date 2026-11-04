package com.minibank.verification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * What the client is told after asking for a code.
 *
 * <p>Carries no indication of whether an account exists at the destination. A request for a number
 * nobody has registered produces exactly this shape, with the same fields filled in, because any
 * difference at all would turn the endpoint into a way to test whether somebody banks here.
 *
 * @param developmentCode present only when delivery is simulated and the deployment has explicitly
 *     opted in. Null in every real environment, and refused outright in the prod profile.
 */
@Schema(name = "VerificationChallenge")
public record VerificationChallenge(
        @Schema(example = "+998 ••• •• 67") String destination,
        @Schema(description = "When the code stops being accepted") Instant expiresAt,
        @Schema(description = "Seconds until another code may be requested", example = "60")
                long resendAvailableInSeconds,
        @Schema(description = "True when no message was actually delivered anywhere")
                boolean deliverySimulated,
        @Schema(description = "DEVELOPMENT ONLY. The code itself, when the deployment exposes it.")
                String developmentCode) {}
