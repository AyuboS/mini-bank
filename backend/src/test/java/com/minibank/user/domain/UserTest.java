package com.minibank.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Duration LOCKOUT = Duration.ofMinutes(15);
    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");

    private static User newUser() {
        return User.create("john", "john@example.com", "hash", "John", "Smith", UserRole.CUSTOMER);
    }

    @Test
    void normalisesUsernameAndEmailToLowerCase() {
        User user = User.create("JoHn", "  John@Example.COM ", "hash", "John", "Smith", UserRole.CUSTOMER);

        assertThat(user.getUsername()).isEqualTo("john");
        assertThat(user.getEmail()).isEqualTo("john@example.com");
    }

    @Test
    void locksOutOnlyAfterTheConfiguredNumberOfFailures() {
        User user = newUser();

        user.registerFailedLogin(3, LOCKOUT, NOW);
        user.registerFailedLogin(3, LOCKOUT, NOW);
        assertThat(user.isCurrentlyLockedOut(NOW)).isFalse();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);

        user.registerFailedLogin(3, LOCKOUT, NOW);
        assertThat(user.isCurrentlyLockedOut(NOW)).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
    }

    @Test
    void theLockoutExpiresOnItsOwn() {
        User user = newUser();
        for (int i = 0; i < 3; i++) {
            user.registerFailedLogin(3, LOCKOUT, NOW);
        }

        assertThat(user.isCurrentlyLockedOut(NOW.plus(LOCKOUT).minusSeconds(1))).isTrue();
        assertThat(user.isCurrentlyLockedOut(NOW.plus(LOCKOUT).plusSeconds(1))).isFalse();
    }

    @Test
    void releasingAnExpiredLockoutRestoresTheAccount() {
        User user = newUser();
        for (int i = 0; i < 3; i++) {
            user.registerFailedLogin(3, LOCKOUT, NOW);
        }

        user.releaseExpiredLockout(NOW.plus(LOCKOUT).plusSeconds(1));

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockoutUntil()).isNull();
    }

    @Test
    void releasingDoesNothingWhileTheLockoutIsStillInForce() {
        User user = newUser();
        for (int i = 0; i < 3; i++) {
            user.registerFailedLogin(3, LOCKOUT, NOW);
        }

        user.releaseExpiredLockout(NOW.plusSeconds(60));

        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
    }

    @Test
    void aSuccessfulLoginClearsTheFailureCounter() {
        User user = newUser();
        user.registerFailedLogin(5, LOCKOUT, NOW);
        user.registerFailedLogin(5, LOCKOUT, NOW);

        user.registerSuccessfulLogin(NOW);

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isEqualTo(NOW);
        assertThat(user.getLockoutUntil()).isNull();
    }

    @Test
    void onlyActiveUsersMayAuthenticate() {
        assertThat(UserStatus.ACTIVE.canAuthenticate()).isTrue();
        assertThat(UserStatus.LOCKED.canAuthenticate()).isFalse();
        assertThat(UserStatus.SUSPENDED.canAuthenticate()).isFalse();
        assertThat(UserStatus.DELETED.canAuthenticate()).isFalse();
    }
}
