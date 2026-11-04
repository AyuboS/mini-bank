package com.minibank.user.dto;

import com.minibank.user.domain.User;
import com.minibank.user.domain.UserRole;
import com.minibank.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A user as returned by the API.
 *
 * <p>There is deliberately no field for the password hash. Mapping is done by hand rather than by
 * exposing the entity, so adding a sensitive column to {@code users} cannot silently start leaking
 * it through every endpoint that returns a user.
 */
@Schema(name = "UserResponse")
public record UserResponse(
        UUID id,
        @Schema(example = "john") String username,
        @Schema(example = "john@example.com") String email,
        String firstName,
        String lastName,
        String phoneNumber,
        @Schema(description = "True once an SMS code has proven control of the number") boolean phoneVerified,
        @Schema(description = "True once a link sent to the address has been opened") boolean emailVerified,
        UserRole role,
        UserStatus status,
        Instant createdAt,
        Instant lastLoginAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber(),
                user.isPhoneVerified(),
                user.isEmailVerified(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getLastLoginAt());
    }
}
