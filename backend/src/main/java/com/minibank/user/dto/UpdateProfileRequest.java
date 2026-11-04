package com.minibank.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial profile update. A {@code null} field means "leave unchanged".
 *
 * <p>The type itself is the authorization boundary: role, status, password hash, balances and ids
 * are simply not representable here, so no amount of extra JSON in the request body can reach them.
 */
@Schema(name = "UpdateProfileRequest", description = "Only the fields present are updated")
public record UpdateProfileRequest(
        @Size(min = 1, max = 100) String firstName,
        @Size(min = 1, max = 100) String lastName,
        @Pattern(
                        regexp = "^\\+?[0-9 ()./-]{7,30}$",
                        message = "must be an international phone number, for example +998901234567")
                @Schema(example = "+998 90 123 45 67")
                String phoneNumber) {}
