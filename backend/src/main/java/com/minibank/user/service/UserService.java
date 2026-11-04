package com.minibank.user.service;

import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.service.AuditEvent;
import com.minibank.audit.service.AuditService;
import com.minibank.auth.domain.RefreshToken;
import com.minibank.auth.service.RefreshTokenService;
import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.error.NotFoundException;
import com.minibank.user.domain.User;
import com.minibank.user.domain.UserStatus;
import com.minibank.user.dto.ChangePasswordRequest;
import com.minibank.user.dto.UpdateProfileRequest;
import com.minibank.user.repository.UserRepository;
import com.minibank.verification.domain.VerificationPurpose;
import com.minibank.verification.service.PhoneNumbers;
import com.minibank.verification.service.VerificationService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profile management for the authenticated user. Registration itself lives in the auth module. */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final VerificationService verificationService;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            AuditService auditService,
            VerificationService verificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.verificationService = verificationService;
    }

    @Transactional(readOnly = true)
    public User getById(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
    }

    /** Applies a partial profile update. Only the three self-service fields can be touched. */
    @Transactional
    public User updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = getById(userId);
        if (request.firstName() != null) {
            user.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName().trim());
        }
        if (request.phoneNumber() != null) {
            /*
             * Normalised on the way in, and the verified flag is cleared with it.
             *
             * A number that changes is a number nobody has proved control of, and leaving the old
             * flag standing would let somebody type a stranger's number into their profile and
             * inherit a verified status they never earned -- which is exactly the credential phone
             * login accepts.
             */
            String normalised = PhoneNumbers.normalize(request.phoneNumber());
            if (!normalised.equals(user.getPhoneNumber())) {
                if (userRepository.existsByPhoneNumber(normalised)) {
                    throw new BusinessRuleException(ErrorCode.PHONE_ALREADY_EXISTS);
                }
                user.setPhoneNumber(normalised);
                user.setPhoneVerifiedAt(null);
            }
        }
        auditService.record(AuditEvent.success(AuditAction.PROFILE_UPDATED).by(user).on("User", userId));
        return user;
    }

    /**
     * Marks the caller's phone number verified once they present a valid code.
     *
     * <p>The verification is looked up by the number currently on the profile, so a code sent for
     * one number cannot confirm another that was swapped in afterwards.
     */
    @Transactional
    public User confirmPhone(UUID userId, String code) {
        User user = getById(userId);
        if (user.getPhoneNumber() == null) {
            throw new BusinessRuleException(
                    ErrorCode.PHONE_NUMBER_INVALID, "Add a phone number to your profile first");
        }
        verificationService.verifyPhoneCode(
                user.getPhoneNumber(), VerificationPurpose.CONTACT_VERIFICATION, code);
        user.confirmPhoneNumber(user.getPhoneNumber(), java.time.Instant.now());
        auditService.record(AuditEvent.success(AuditAction.PHONE_VERIFIED).by(user).on("User", userId));
        log.info("Phone number verified for user {}", userId);
        return user;
    }

    /**
     * Changes the caller's password.
     *
     * <p>Every existing session is revoked afterwards. A password change is the action a user takes
     * when they believe they have been compromised, so leaving previously issued refresh tokens
     * alive would defeat the point.
     */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = getById(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            auditService.recordIndependently(
                    AuditEvent.failure(AuditAction.PASSWORD_CHANGED).by(user).on("User", userId));
            throw new BusinessRuleException(ErrorCode.PASSWORD_MISMATCH);
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        int revoked = refreshTokenService.revokeAllSessions(userId, RefreshToken.RevocationReason.REVOKED_BY_SYSTEM);
        auditService.record(AuditEvent.success(AuditAction.PASSWORD_CHANGED)
                .by(user)
                .on("User", userId)
                .with("revokedSessions", revoked));
        log.info("Password changed for user {}, {} sessions revoked", userId, revoked);
    }

    /** Administrative status change. Suspending a user also terminates their sessions. */
    @Transactional
    public User changeStatus(UUID userId, UserStatus newStatus, UUID adminId, String adminUsername) {
        User user = getById(userId);
        UserStatus previous = user.getStatus();
        user.setStatus(newStatus);
        if (!newStatus.canAuthenticate()) {
            refreshTokenService.revokeAllSessions(userId, RefreshToken.RevocationReason.REVOKED_BY_SYSTEM);
        }
        if (newStatus == UserStatus.ACTIVE) {
            user.releaseExpiredLockout(java.time.Instant.now());
            user.setFailedLoginAttempts(0);
            user.setLockoutUntil(null);
        }
        auditService.record(AuditEvent.success(AuditAction.USER_STATUS_CHANGED)
                .by(adminId, adminUsername)
                .on("User", userId)
                .with("from", previous)
                .with("to", newStatus));
        log.info("User {} status changed {} -> {} by {}", userId, previous, newStatus, adminUsername);
        return user;
    }
}
