package com.minibank.user.domain;

import com.minibank.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import lombok.Getter;
import lombok.Setter;

/**
 * An authenticated principal of the platform.
 *
 * <p>Only the BCrypt hash of the password is held; the plain text value never reaches this class.
 * Username and email are normalised to lower case on the way in so that the database unique
 * constraints are effectively case-insensitive.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends AuditedEntity {

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /**
     * Stored in E.164 form, always. See {@code PhoneNumbers}: the same number typed three ways must
     * not become three accounts, and a partial unique index enforces that at the database level.
     */
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role = UserRole.CUSTOMER;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "lockout_until")
    private Instant lockoutUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected User() {
        // for JPA
    }

    public static User create(
            String username,
            String email,
            String passwordHash,
            String firstName,
            String lastName,
            UserRole role) {
        User user = new User();
        user.username = normalize(username);
        user.email = normalize(email);
        user.passwordHash = passwordHash;
        user.firstName = firstName;
        user.lastName = lastName;
        user.role = role;
        user.status = UserStatus.ACTIVE;
        return user;
    }

    public static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public boolean isPhoneVerified() {
        return phoneVerifiedAt != null;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    /**
     * Attaches a proven phone number.
     *
     * <p>Setting the number and marking it verified is one operation on purpose. Two setters would
     * make it possible to store a number that was never confirmed and have it look confirmed, which
     * is precisely the state phone login must never accept.
     */
    public void confirmPhoneNumber(String e164, Instant now) {
        this.phoneNumber = e164;
        this.phoneVerifiedAt = now;
    }

    public void markEmailVerified(Instant now) {
        this.emailVerifiedAt = now;
    }

    /**
     * True while a temporary lockout is still in force. A lockout that has elapsed is treated as
     * expired here rather than requiring a background job to clear it.
     */
    public boolean isCurrentlyLockedOut(Instant now) {
        return lockoutUntil != null && lockoutUntil.isAfter(now);
    }

    /** Records a failed login and applies a temporary lockout once the threshold is reached. */
    public void registerFailedLogin(int maxAttempts, java.time.Duration lockoutDuration, Instant now) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockoutUntil = now.plus(lockoutDuration);
            status = UserStatus.LOCKED;
        }
    }

    /** Clears the failure counter and lockout after a successful authentication. */
    public void registerSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockoutUntil = null;
        lastLoginAt = now;
        if (status == UserStatus.LOCKED) {
            status = UserStatus.ACTIVE;
        }
    }

    /** Clears an elapsed lockout so the user can attempt to authenticate again. */
    public void releaseExpiredLockout(Instant now) {
        if (status == UserStatus.LOCKED && !isCurrentlyLockedOut(now)) {
            status = UserStatus.ACTIVE;
            failedLoginAttempts = 0;
            lockoutUntil = null;
        }
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
