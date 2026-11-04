package com.minibank.common.security;

import com.minibank.common.error.ApiException;
import com.minibank.common.error.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Reads the authenticated principal out of the Spring Security context. */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof AuthenticatedUser user ? Optional.of(user) : Optional.empty();
    }

    /**
     * @throws ApiException with {@link ErrorCode#UNAUTHENTICATED} when there is no authenticated user
     */
    public static AuthenticatedUser requireCurrentUser() {
        return currentUser().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    public static Optional<UUID> currentUserId() {
        return currentUser().map(AuthenticatedUser::getId);
    }
}
