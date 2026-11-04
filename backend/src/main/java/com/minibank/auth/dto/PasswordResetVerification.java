package com.minibank.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Exchange a reset code for the single-use ticket that authorises the new password. */
@Schema(name = "PasswordResetVerification")
public record PasswordResetVerification(
        @NotBlank @Schema(example = "+37120000000") String phoneNumber,
        @NotBlank @Pattern(regexp = "^[0-9]{4,8}$", message = "must be the numeric code from the message")
                @Schema(example = "482913")
                String code) {}
