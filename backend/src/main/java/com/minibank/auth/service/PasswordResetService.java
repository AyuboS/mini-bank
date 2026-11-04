package com.minibank.auth.service;

import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.service.AuditEvent;
import com.minibank.audit.service.AuditService;
import com.minibank.auth.domain.RefreshToken;
import com.minibank.auth.dto.PasswordResetCompletion;
import com.minibank.auth.dto.PasswordResetRequest;
import com.minibank.auth.dto.PasswordResetVerification;
import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
import com.minibank.notification.center.domain.NotificationSeverity;
import com.minibank.notification.center.domain.NotificationType;
import com.minibank.notification.center.service.NotificationService;
import com.minibank.user.domain.User;
import com.minibank.user.repository.UserRepository;
import com.minibank.verification.domain.VerificationChannel;
import com.minibank.verification.domain.VerificationCode;
import com.minibank.verification.domain.VerificationPurpose;
import com.minibank.verification.dto.VerificationChallenge;
import com.minibank.verification.dto.VerificationTicket;
import com.minibank.verification.service.PhoneNumbers;
import com.minibank.verification.service.VerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "I forgot my password."
 *
 * <h2>Three steps, and why</h2>
 *
 * <ol>
 *   <li><b>request</b> — a one-time code goes to the number on the account
 *   <li><b>verify</b> — the code is exchanged for a short-lived, single-use ticket
 *   <li><b>complete</b> — the ticket authorises one password change
 * </ol>
 *
 * <p>The ticket exists so the code is spent the moment it is proven, rather than being held by the
 * client while the customer types a new password. A code that stays valid across that gap is a code
 * that can be brute-forced across that gap.
 *
 * <h2>What this endpoint refuses to tell you</h2>
 *
 * <p>An unknown number produces exactly the response a known one does, and nothing is sent. A reset
 * form that answers "no account with that number" is a free account-enumeration oracle, and it is
 * the first place anyone would look for one. The cost is a customer who mistypes their number and
 * waits for a message that never comes; the alternative is handing an attacker the bank's customer
 * list, one guess at a time.
 *
 * <h2>Afterwards</h2>
 *
 * <p>Every session is revoked. If the password was reset because somebody else had the account, the
 * reset must remove them, and leaving their refresh token alive would mean it did not.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final VerificationService verificationService;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public PasswordResetService(
            VerificationService verificationService,
            UserRepository users,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            AuditService auditService,
            NotificationService notificationService) {
        this.verificationService = verificationService;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    /** Step one. Always answers the same shape, whether or not the number is known. */
    @Transactional
    public VerificationChallenge request(PasswordResetRequest request) {
        auditService.recordIndependently(AuditEvent.success(AuditAction.PASSWORD_RESET_REQUESTED)
                .with("destination", PhoneNumbers.mask(PhoneNumbers.normalize(request.phoneNumber()))));
        return verificationService.requestPhoneCode(
                request.phoneNumber(), VerificationPurpose.PASSWORD_RESET, null);
    }

    /** Step two. Spends the code and hands back a ticket. */
    @Transactional
    public VerificationTicket verify(PasswordResetVerification request) {
        VerificationCode record = verificationService.verifyPhoneCode(
                request.phoneNumber(), VerificationPurpose.PASSWORD_RESET, request.code());
        return verificationService.issueTicket(record);
    }

    /** Step three. Burns the ticket, sets the password, and ends every session. */
    @Transactional
    public void complete(PasswordResetCompletion request) {
        String destination = PhoneNumbers.normalize(request.phoneNumber());
        verificationService.redeemTicket(
                VerificationChannel.PHONE, destination, VerificationPurpose.PASSWORD_RESET, request.ticket());

        User user = users.findByPhoneNumber(destination)
                // A ticket that redeemed against a number with no account should not be reachable.
                // If it happens, the reset stops here rather than guessing whose password to change.
                .orElseThrow(() -> new BusinessRuleException(
                        ErrorCode.VERIFICATION_REQUIRED, "This verification is no longer valid, start again"));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // Also clears any lockout: somebody who has just proven control of the number should not be
        // held out by the failed attempts that led them here.
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);

        int revoked = refreshTokenService.revokeAllSessions(
                user.getId(), RefreshToken.RevocationReason.REVOKED_BY_SYSTEM);

        auditService.record(AuditEvent.success(AuditAction.PASSWORD_RESET_COMPLETED)
                .by(user)
                .on("User", user.getId())
                .with("revokedSessions", revoked));

        notificationService.publish(
                user,
                NotificationType.SECURITY,
                NotificationSeverity.CRITICAL,
                "Your password was reset",
                "All devices were signed out. If this was not you, contact us immediately.",
                "User",
                user.getId());

        log.info("Password reset completed for user {}, {} sessions revoked", user.getId(), revoked);
    }
}
