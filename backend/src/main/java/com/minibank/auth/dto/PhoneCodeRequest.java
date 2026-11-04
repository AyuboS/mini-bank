package com.minibank.auth.dto;

import com.minibank.verification.domain.VerificationPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Asks for a one-time code by SMS.
 *
 * <p>The purpose is part of the request because it is part of what the code authorises: one
 * obtained to confirm a number must not also work as a way to sign in.
 */
@Schema(name = "PhoneCodeRequest")
public record PhoneCodeRequest(
        @NotBlank @Size(max = 30) @Schema(example = "+998 90 123 45 67") String phoneNumber,
        @NotNull @Schema(example = "LOGIN") VerificationPurpose purpose) {}
