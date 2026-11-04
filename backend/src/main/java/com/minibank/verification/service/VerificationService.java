package com.minibank.verification.service;

import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.service.AuditEvent;
import com.minibank.audit.service.AuditService;
import com.minibank.common.error.ApiException;
import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
import com.minibank.notification.config.NotificationProperties;
import com.minibank.notification.email.EmailService;
import com.minibank.notification.sms.SmsService;
import com.minibank.user.domain.User;
import com.minibank.user.repository.UserRepository;
import com.minibank.verification.config.VerificationProperties;
import com.minibank.verification.domain.VerificationChannel;
import com.minibank.verification.domain.VerificationCode;
import com.minibank.verification.domain.VerificationPurpose;
import com.minibank.verification.dto.VerificationChallenge;
import com.minibank.verification.dto.VerificationTicket;
import com.minibank.verification.repository.VerificationCodeRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issuing, delivering and checking verification codes.
 *
 * <p>Both channels run through the same five rules, which is the reason they share an entity:
 *
 * <ol>
 *   <li>a code lives for a fixed, short time;
 *   <li>a limited number of wrong guesses burns it;
 *   <li>a resend cannot be requested faster than the cooldown;
 *   <li>a successful check consumes it immediately, so it works exactly once;
 *   <li>only one code is ever live for a destination and purpose at a time.
 * </ol>
 *
 * <p>The fifth is what a naive implementation gets wrong: appending a new row per request leaves
 * every previously sent code still valid, so the attacker's guessing space grows with each resend
 * instead of resetting.
 *
 * <p>No method here logs a code, a token or a full phone number.
 */
@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    private final VerificationCodeRepository repository;
    private final UserRepository userRepository;
    private final VerificationSecrets secrets;
    private final VerificationAttemptService attemptService;
    private final SmsService smsService;
    private final EmailService emailService;
    private final VerificationProperties properties;
    private final NotificationProperties notificationProperties;
    private final AuditService auditService;
    private final Clock clock;

    public VerificationService(
            VerificationCodeRepository repository,
            UserRepository userRepository,
            VerificationSecrets secrets,
            VerificationAttemptService attemptService,
            SmsService smsService,
            EmailService emailService,
            VerificationProperties properties,
            NotificationProperties notificationProperties,
            AuditService auditService,
            Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.secrets = secrets;
        this.attemptService = attemptService;
        this.smsService = smsService;
        this.emailService = emailService;
        this.properties = properties;
        this.notificationProperties = notificationProperties;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ phone

    /**
     * Sends a one-time code to a phone number.
     *
     * <p>For {@link VerificationPurpose#LOGIN} an unknown number produces a challenge that looks
     * identical to a real one and sends nothing. Anything else would let somebody discover who
     * banks here, and would also let them use the endpoint to send messages to strangers.
     */
    @Transactional
    public VerificationChallenge requestPhoneCode(String rawPhoneNumber, VerificationPurpose purpose, User caller) {
        String destination = PhoneNumbers.normalize(rawPhoneNumber);
        Instant now = clock.instant();

        User subject = resolvePhoneSubject(destination, purpose, caller);
        if (subject == null
                && (purpose == VerificationPurpose.LOGIN || purpose == VerificationPurpose.PASSWORD_RESET)) {
            log.info("Phone code requested for an unregistered number; nothing sent");
            return silentChallenge(destination, now, properties.phone().codeTtl());
        }

        Duration ttl = properties.phone().codeTtl();
        String code = secrets.numericCode(VerificationChannel.PHONE.secretLength());
        VerificationCode record = upsert(
                VerificationChannel.PHONE,
                purpose,
                destination,
                subject,
                secrets.hash(code),
                properties.phone().resendCooldown(),
                properties.phone().maxAttempts(),
                ttl,
                now);
        repository.save(record);

        deliverSms(destination, code, ttl);

        auditService.record(AuditEvent.success(AuditAction.PHONE_VERIFICATION_REQUESTED)
                .by(subject == null ? null : subject.getId(), subject == null ? null : subject.getUsername())
                .on("VerificationCode", record.getId())
                .with("purpose", purpose)
                .with("destination", PhoneNumbers.mask(destination)));

        return new VerificationChallenge(
                PhoneNumbers.mask(destination),
                record.getExpiresAt(),
                properties.phone().resendCooldown().toSeconds(),
                smsService.isSimulated(),
                exposedCode(code));
    }

    /**
     * Checks a phone code and consumes it.
     *
     * @return the consumed record, carrying the ticket and the user it belongs to
     */
    @Transactional
    public VerificationCode verifyPhoneCode(String rawPhoneNumber, VerificationPurpose purpose, String code) {
        String destination = PhoneNumbers.normalize(rawPhoneNumber);
        VerificationCode record = repository
                .findByChannelAndDestinationAndPurpose(VerificationChannel.PHONE, destination, purpose)
                .orElseThrow(() -> {
                    // No live code: the same answer a wrong code gets, so the two are
                    // indistinguishable to somebody probing for registered numbers.
                    recordOtpFailure(null, destination, "NO_CODE_IN_PROGRESS");
                    return new ApiException(ErrorCode.VERIFICATION_CODE_INVALID);
                });

        return checkAndConsume(record, code, destination);
    }

    // ------------------------------------------------------------------ email

    /**
     * Sends a verification link to a user's email address.
     *
     * <p>Silently does nothing when the address is already verified, so a duplicate click on
     * "resend" cannot invalidate a link the customer is about to use.
     */
    @Transactional
    public VerificationChallenge requestEmailVerification(User user) {
        Instant now = clock.instant();
        String destination = user.getEmail();

        if (user.isEmailVerified()) {
            return silentChallenge(destination, now, properties.email().tokenTtl());
        }

        Duration ttl = properties.email().tokenTtl();
        String token = secrets.urlToken();
        VerificationCode record = upsert(
                VerificationChannel.EMAIL,
                VerificationPurpose.CONTACT_VERIFICATION,
                destination,
                user,
                secrets.hash(token),
                properties.email().resendCooldown(),
                // A link is not guessed at, so an attempt counter is not what protects it; the value
                // is only here because the column is shared with the phone channel.
                properties.phone().maxAttempts(),
                ttl,
                now);
        repository.save(record);

        deliverVerificationEmail(user, token, ttl);

        auditService.record(AuditEvent.success(AuditAction.EMAIL_VERIFICATION_REQUESTED)
                .by(user)
                .on("VerificationCode", record.getId()));

        return new VerificationChallenge(
                destination,
                record.getExpiresAt(),
                properties.email().resendCooldown().toSeconds(),
                emailService.isSimulated(),
                null);
    }

    /** Redeems a link token and marks the address verified. */
    @Transactional
    public User verifyEmailToken(String token) {
        VerificationCode record = repository
                .findByChannelAndSecretHash(VerificationChannel.EMAIL, secrets.hash(token))
                .orElseThrow(() -> new ApiException(ErrorCode.VERIFICATION_CODE_INVALID));

        Instant now = clock.instant();
        if (record.isConsumed()) {
            throw new ApiException(ErrorCode.VERIFICATION_CODE_INVALID);
        }
        if (record.isExpiredAt(now)) {
            auditService.recordIndependently(AuditEvent.failure(AuditAction.OTP_EXPIRED)
                    .on("VerificationCode", record.getId())
                    .with("channel", VerificationChannel.EMAIL));
            throw new ApiException(ErrorCode.VERIFICATION_CODE_EXPIRED);
        }

        User user = record.getUser();
        if (user == null) {
            throw new ApiException(ErrorCode.VERIFICATION_CODE_INVALID);
        }

        record.consume(null, now);
        user.markEmailVerified(now);
        userRepository.save(user);

        auditService.record(AuditEvent.success(AuditAction.EMAIL_VERIFIED).by(user).on("User", user.getId()));
        log.info("Email verified for user {}", user.getId());
        return user;
    }

    // ----------------------------------------------------------------- tickets

    /** Hands out the opaque proof that goes with a freshly verified destination. */
    @Transactional
    public VerificationTicket issueTicket(VerificationCode record) {
        String token = secrets.urlToken();
        record.consume(secrets.hash(token), clock.instant());
        repository.save(record);
        return new VerificationTicket(record.getDestination(), token, record.getTicketExpiresAt());
    }

    /**
     * Validates a ticket and burns it.
     *
     * @throws BusinessRuleException with {@link ErrorCode#VERIFICATION_REQUIRED} if it is missing,
     *     unknown or expired
     */
    @Transactional
    public void redeemTicket(VerificationChannel channel, String destination, VerificationPurpose purpose,
            String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessRuleException(
                    ErrorCode.VERIFICATION_REQUIRED, "Verify the number before creating the account");
        }

        VerificationCode record = repository
                .findByChannelAndDestinationAndPurposeAndTicketHash(channel, destination, purpose, secrets.hash(token))
                .orElseThrow(() -> new BusinessRuleException(
                        ErrorCode.VERIFICATION_REQUIRED, "This verification is no longer valid, start again"));

        if (!record.isTicketUsable(clock.instant())) {
            throw new BusinessRuleException(
                    ErrorCode.VERIFICATION_REQUIRED, "This verification has expired, request a new code");
        }
        record.redeemTicket();
        repository.save(record);
    }

    /** Removes rows that are past every deadline they carry. */
    @Transactional
    public int purgeExpired() {
        int removed = repository.deleteExpiredBefore(clock.instant());
        if (removed > 0) {
            log.info("Purged {} spent verification records", removed);
        }
        return removed;
    }

    // ---------------------------------------------------------------- internals

    /**
     * The single place a presented secret is checked.
     *
     * <p>Order matters here. Expiry is tested before the attempt counter, so a code that simply ran
     * out of time reports that rather than silently eating one of the customer's five guesses.
     */
    private VerificationCode checkAndConsume(VerificationCode record, String presented, String destination) {
        Instant now = clock.instant();
        User owner = record.getUser();

        if (record.isConsumed()) {
            recordOtpFailure(owner, destination, "ALREADY_USED");
            throw new ApiException(ErrorCode.VERIFICATION_CODE_INVALID);
        }
        if (record.isExpiredAt(now)) {
            auditService.recordIndependently(AuditEvent.failure(AuditAction.OTP_EXPIRED)
                    .by(owner == null ? null : owner.getId(), owner == null ? null : owner.getUsername())
                    .on("VerificationCode", record.getId()));
            throw new ApiException(ErrorCode.VERIFICATION_CODE_EXPIRED);
        }
        if (!record.hasAttemptsLeft()) {
            recordOtpFailure(owner, destination, "ATTEMPTS_EXHAUSTED");
            throw new ApiException(ErrorCode.VERIFICATION_TOO_MANY_ATTEMPTS);
        }

        if (!secrets.matches(presented, record.getSecretHash())) {
            /*
             * The increment is committed separately, because this method is about to throw.
             *
             * Counting the attempt on `record` and letting the rollback take it with us would leave
             * the limit permanently at zero used -- unlimited guesses at a six digit number, which
             * is the whole attack the limit exists to stop.
             */
            boolean exhausted = attemptService.registerFailure(record.getId());
            recordOtpFailure(owner, destination, "WRONG_CODE");
            // The last permitted guess reports exhaustion rather than "wrong again", so the client
            // can tell the customer to request a new code instead of retrying into a dead end.
            throw new ApiException(
                    exhausted ? ErrorCode.VERIFICATION_TOO_MANY_ATTEMPTS : ErrorCode.VERIFICATION_CODE_INVALID);
        }

        auditService.record(AuditEvent.success(AuditAction.PHONE_VERIFIED)
                .by(owner == null ? null : owner.getId(), owner == null ? null : owner.getUsername())
                .on("VerificationCode", record.getId())
                .with("destination", PhoneNumbers.mask(destination)));
        return record;
    }

    /**
     * Finds or creates the row for a destination, enforcing the resend cooldown.
     *
     * <p>Reusing the row is what guarantees only one code is ever live.
     */
    private VerificationCode upsert(
            VerificationChannel channel,
            VerificationPurpose purpose,
            String destination,
            User subject,
            String secretHash,
            Duration cooldown,
            int maxAttempts,
            Duration ttl,
            Instant now) {

        Optional<VerificationCode> existing =
                repository.findByChannelAndDestinationAndPurpose(channel, destination, purpose);

        if (existing.isPresent()) {
            VerificationCode record = existing.get();
            if (record.isWithinResendCooldown(cooldown, now)) {
                throw new BusinessRuleException(
                        ErrorCode.VERIFICATION_RESEND_TOO_SOON,
                        "Another code can be sent in %d seconds".formatted(record.resendAvailableIn(cooldown, now)));
            }
            record.reissue(secretHash, ttl, now);
            record.setUser(subject);
            return record;
        }

        return VerificationCode.start(channel, purpose, destination, subject, secretHash, maxAttempts, ttl, now);
    }

    /**
     * Who the verification is for.
     *
     * @throws BusinessRuleException if a registration is attempted for a number already in use
     */
    private User resolvePhoneSubject(String destination, VerificationPurpose purpose, User caller) {
        return switch (purpose) {
            case LOGIN -> userRepository
                    .findByPhoneNumber(destination)
                    // A number nobody has proven is not a credential, so no code is sent for it.
                    // Otherwise typing a stranger's number into a profile would start delivering
                    // them sign-in codes for an account they have never heard of.
                    .filter(User::isPhoneVerified)
                    .orElse(null);
            case CONTACT_VERIFICATION -> caller;
            case PASSWORD_RESET -> userRepository
                    .findByPhoneNumber(destination)
                    .filter(User::isPhoneVerified)
                    // Same silence as LOGIN: an unknown number gets an identical-looking challenge
                    // and no message. "No account with that number" is a free account-enumeration
                    // oracle, and a password reset form is exactly where somebody would look for one.
                    .orElse(null);
            case REGISTRATION -> {
                // Registration is one of the few places where telling the truth is the right call:
                // the alternative is a customer who verifies a code and then cannot proceed, with no
                // explanation. The same fact is already revealed for a taken username or email.
                if (userRepository.existsByPhoneNumber(destination)) {
                    throw new BusinessRuleException(ErrorCode.PHONE_ALREADY_EXISTS);
                }
                yield null;
            }
        };
    }

    private void deliverSms(String destination, String code, Duration ttl) {
        try {
            smsService.send(destination, VerificationMessages.sms(code, ttl.toMinutes()));
        } catch (SmsService.SmsDeliveryException ex) {
            // Surfaced rather than swallowed. Reporting success for a message that never left would
            // leave the customer staring at an OTP screen no code is ever going to arrive for.
            log.error("Could not deliver a verification SMS via {}", smsService.providerName());
            throw new ApiException(ErrorCode.DELIVERY_FAILED);
        }
    }

    private void deliverVerificationEmail(User user, String token, Duration ttl) {
        String link = "%s/verify-email?token=%s"
                .formatted(
                        notificationProperties.appBaseUrl().replaceAll("/+$", ""),
                        URLEncoder.encode(token, StandardCharsets.UTF_8));
        try {
            emailService.send(
                    user.getEmail(),
                    VerificationMessages.emailSubject(),
                    VerificationMessages.emailText(user.getFirstName(), link, ttl.toHours()),
                    VerificationMessages.emailHtml(user.getFirstName(), link, ttl.toHours()));
        } catch (EmailService.EmailDeliveryException ex) {
            log.error("Could not deliver a verification email via {}", emailService.providerName());
            throw new ApiException(ErrorCode.DELIVERY_FAILED);
        }
    }

    /** A challenge for a destination nothing was sent to, shaped exactly like a real one. */
    private VerificationChallenge silentChallenge(String destination, Instant now, Duration ttl) {
        boolean phone = destination.startsWith("+");
        return new VerificationChallenge(
                phone ? PhoneNumbers.mask(destination) : destination,
                now.plus(ttl),
                properties.phone().resendCooldown().toSeconds(),
                phone ? smsService.isSimulated() : emailService.isSimulated(),
                null);
    }

    /**
     * The code, but only when it went nowhere and the deployment asked for it.
     *
     * <p>Both conditions are required. A configuration flag alone would expose live codes the moment
     * somebody pointed a real provider at a deployment that still had the flag set.
     */
    private String exposedCode(String code) {
        return notificationProperties.exposeCodes() && smsService.isSimulated() ? code : null;
    }

    private void recordOtpFailure(User owner, String destination, String reason) {
        auditService.recordIndependently(AuditEvent.failure(AuditAction.OTP_FAILED)
                .by(owner == null ? null : owner.getId(), owner == null ? null : owner.getUsername())
                .with("reason", reason)
                .with("destination", destination.startsWith("+") ? PhoneNumbers.mask(destination) : destination));
    }
}
