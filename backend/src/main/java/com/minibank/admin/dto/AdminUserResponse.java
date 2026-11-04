package com.minibank.admin.dto;

import com.minibank.user.domain.User;
import com.minibank.user.domain.UserRole;
import com.minibank.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A user as seen by an administrator.
 *
 * <p>The phone number is masked. Administrators need to identify and support customers, which the
 * last four digits are sufficient for; the full number is personal data that a back-office list view
 * has no operational need to display.
 */
@Schema(name = "AdminUserResponse")
public record AdminUserResponse(
        UUID id,
        String username,
        String email,
        String fullName,
        @Schema(example = "*******5678") String maskedPhoneNumber,
        UserRole role,
        UserStatus status,
        int failedLoginAttempts,
        Instant lockoutUntil,
        Instant lastLoginAt,
        Instant createdAt) {

    private static final int VISIBLE_DIGITS = 4;

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.fullName(),
                mask(user.getPhoneNumber()),
                user.getRole(),
                user.getStatus(),
                user.getFailedLoginAttempts(),
                user.getLockoutUntil(),
                user.getLastLoginAt(),
                user.getCreatedAt());
    }

    static String mask(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }
        String digitsOnly = phoneNumber.replaceAll("\\D", "");
        if (digitsOnly.length() <= VISIBLE_DIGITS) {
            return "*".repeat(digitsOnly.length());
        }
        String tail = digitsOnly.substring(digitsOnly.length() - VISIBLE_DIGITS);
        return "*".repeat(digitsOnly.length() - VISIBLE_DIGITS) + tail;
    }
}
