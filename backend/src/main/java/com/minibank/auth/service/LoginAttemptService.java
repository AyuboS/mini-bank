package com.minibank.auth.service;

import com.minibank.common.config.SecurityProperties;
import com.minibank.user.domain.User;
import com.minibank.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the failed-login counter and the temporary lockout.
 *
 * <p>Separated from {@link AuthService} for a specific reason: recording a failure has to be
 * committed even though the login attempt ends by throwing. If the counter were incremented inside
 * the transaction that then rolls back with {@code AUTH_INVALID_CREDENTIALS}, the increment would
 * be undone and brute-force protection would silently never trigger. Every method here therefore
 * runs in its own transaction, and being a separate bean is what makes the proxy honour that.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final UserRepository userRepository;
    private final SecurityProperties securityProperties;
    private final Clock clock;

    public LoginAttemptService(
            UserRepository userRepository, SecurityProperties securityProperties, Clock clock) {
        this.userRepository = userRepository;
        this.securityProperties = securityProperties;
        this.clock = clock;
    }

    /**
     * Records a failed attempt.
     *
     * @return true if this failure pushed the account into lockout
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registerFailure(UUID userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        Instant now = clock.instant();
        boolean wasLocked = user.isCurrentlyLockedOut(now);
        user.registerFailedLogin(
                securityProperties.lockout().maxFailedAttempts(), securityProperties.lockout().duration(), now);
        boolean nowLocked = user.isCurrentlyLockedOut(now);
        if (nowLocked && !wasLocked) {
            log.warn(
                    "User {} locked out until {} after {} failed login attempts",
                    userId,
                    user.getLockoutUntil(),
                    user.getFailedLoginAttempts());
        }
        return nowLocked && !wasLocked;
    }

    /** Clears the counter and any lockout after a successful authentication. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerSuccess(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> user.registerSuccessfulLogin(clock.instant()));
    }
}
