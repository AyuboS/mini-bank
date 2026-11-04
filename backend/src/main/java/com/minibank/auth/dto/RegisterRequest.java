package com.minibank.auth.dto;

import com.minibank.common.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * New customer registration.
 *
 * <p>Role and status are not accepted from the client. A registration always produces an ACTIVE
 * CUSTOMER; there is no request shape that can create an administrator.
 */
@Schema(name = "RegisterRequest")
public record RegisterRequest(
        @NotBlank
                @Size(min = 3, max = 50)
                @Pattern(
                        regexp = "^[a-zA-Z0-9._-]+$",
                        message = "may contain only letters, digits, dot, underscore and hyphen")
                @Schema(example = "john")
                String username,
        @NotBlank @Email @Size(max = 255) @Schema(example = "john@example.com") String email,
        @NotBlank @StrongPassword @Schema(example = "StrongPassword123!") String password,
        @NotBlank @Size(min = 1, max = 100) @Schema(example = "John") String firstName,
        @NotBlank @Size(min = 1, max = 100) @Schema(example = "Smith") String lastName,
        @Size(max = 30)
                @Schema(
                        example = "+998901234567",
                        description = "Optional. Must be accompanied by a verificationToken proving control of it.")
                String phoneNumber,
        @Size(max = 200)
                @Schema(description = "The token returned by POST /auth/phone/confirm for this number")
                String verificationToken) {

    /** The password must not reach a log through a record's generated toString. */
    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", email=" + email + ", password=***]";
    }
}
