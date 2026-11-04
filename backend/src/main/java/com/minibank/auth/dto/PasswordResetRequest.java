package com.minibank.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Start a password reset by asking for a code at a phone number. */
@Schema(name = "PasswordResetRequest")
public record PasswordResetRequest(
        @NotBlank
                @Schema(
                        description =
                                "The number on the account. An unknown number produces the same response "
                                        + "as a known one and sends nothing.",
                        example = "+37120000000")
                String phoneNumber) {}
