package com.minibank.auth.dto;

import com.minibank.common.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Set the new password.
 *
 * <p>The ticket is the authorisation. It is issued only after a code was verified, is single use,
 * and expires quickly — so a reset cannot be completed by anyone who did not just prove control of
 * the number.
 */
@Schema(name = "PasswordResetCompletion")
public record PasswordResetCompletion(
        @NotBlank @Schema(example = "+37120000000") String phoneNumber,
        @NotBlank @Schema(description = "The ticket returned by the verification step") String ticket,
        @NotBlank @StrongPassword String newPassword) {}
