package com.minibank.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credentials presented at login.
 *
 * <p>The field is still called {@code username} for compatibility, but it accepts a username, an
 * email address or a verified phone number: people do not reliably remember which one they signed
 * up with, and all three are unique.
 */
@Schema(name = "LoginRequest")
public record LoginRequest(
        @NotBlank
                @Size(max = 255)
                @Schema(description = "Username, email address, or verified phone number", example = "john")
                String username,
        @NotBlank @Size(max = 200) @Schema(example = "StrongPassword123!") String password) {

    /** Keeps the password out of accidental {@code toString()} output in logs and stack traces. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
