package com.minibank.auth.service;

import com.minibank.account.service.AccountService;
import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.service.AuditEvent;
import com.minibank.audit.service.AuditService;
import com.minibank.auth.dto.AuthenticationResponse;
import com.minibank.auth.dto.LoginRequest;
import com.minibank.auth.dto.RegisterRequest;
import com.minibank.common.error.ApiException;
import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.money.CurrencyCode;
import com.minibank.common.security.JwtService;
import com.minibank.user.domain.User;
import com.minibank.user.domain.UserRole;
import com.minibank.user.dto.UserResponse;
import com.minibank.user.repository.UserRepository;
import com.minibank.verification.domain.VerificationChannel;
import com.minibank.verification.domain.VerificationCode;
import com.minibank.verification.domain.VerificationPurpose;
import com.minibank.verification.dto.VerificationChallenge;
import com.minibank.verification.dto.VerificationTicket;
import com.minibank.verification.service.PhoneNumbers;
import com.minibank.verification.service.VerificationService;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and login.
 *
 * <p>{@code login} is intentionally not annotated {@code @Transactional}. A failed attempt must
 * persist its side effects (the incremented failure counter, the audit record) while still ending
 * in an exception, which is incompatible with a single surrounding transaction that would roll them
 * back. State changes are therefore delegated to {@link LoginAttemptService} and {@link
 * AuditService}, each committing independently.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * A real BCrypt hash of a value nobody knows. Verifying against it when the username does not
     * exist keeps the response time of "no such user" indistinguishable from "wrong password", so
     * the endpoint cannot be used to enumerate valid usernames.
     */
    private static final String DUMMY_HASH = "$2a$12$C6UzMDM.H6dfI/f/IKcEe.5AaG7cxWpJ2bXKgtjXk9Fj0BSJ1ROBu";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final AccountService accountService;
    private final AuditService auditService;
    private final VerificationService verificationService;
    private final Clock clock;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            LoginAttemptService loginAttemptService,
            AccountService accountService,
            AuditService auditService,
            VerificationService verificationService,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptService = loginAttemptService;
        this.accountService = accountService;
        this.auditService = auditService;
        this.verificationService = verificationService;
        this.clock = clock;
    }

    /**
     * Creates a customer and opens their first EUR account in the same transaction, so a
     * registration can never leave a user without an account.
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = User.normalize(request.username());
        String email = User.normalize(request.email());

        if (userRepository.existsByUsername(username)) {
            throw new BusinessRuleException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.create(
                username,
                email,
                passwordEncoder.encode(request.password()),
                request.firstName().trim(),
                request.lastName().trim(),
                UserRole.CUSTOMER);

        /*
         * A phone number arrives already proven, or not at all.
         *
         * The ticket is what proves it: it was handed out moments ago to whoever answered an SMS at
         * this number, and redeeming it here burns it. Accepting a bare number instead would let
         * anybody claim a stranger's -- and since a verified number is a login credential, that
         * would be an account takeover primitive rather than a profile field.
         */
        Instant now = clock.instant();
        if (request.phoneNumber() != null && !request.phoneNumber().isBlank()) {
            String phoneNumber = PhoneNumbers.normalize(request.phoneNumber());
            if (userRepository.existsByPhoneNumber(phoneNumber)) {
                throw new BusinessRuleException(ErrorCode.PHONE_ALREADY_EXISTS);
            }
            verificationService.redeemTicket(
                    VerificationChannel.PHONE,
                    phoneNumber,
                    VerificationPurpose.REGISTRATION,
                    request.verificationToken());
            user.confirmPhoneNumber(phoneNumber, now);
        }

        User saved = userRepository.save(user);
        accountService.openAccount(saved, CurrencyCode.EUR);

        auditService.record(AuditEvent.success(AuditAction.USER_REGISTERED)
                .by(saved)
                .on("User", saved.getId())
                .with("phoneVerified", saved.isPhoneVerified()));
        if (saved.isPhoneVerified()) {
            auditService.record(AuditEvent.success(AuditAction.PHONE_VERIFIED).by(saved).on("User", saved.getId()));
        }
        log.info("Registered user {} ({})", saved.getUsername(), saved.getId());

        /*
         * The verification email is best effort.
         *
         * A relay that is down must not undo a registration that otherwise succeeded: the customer
         * would be told their sign-up failed while the row sat committed, and the retry would then
         * collide with their own username. They can ask for the link again at any time.
         */
        try {
            verificationService.requestEmailVerification(saved);
        } catch (RuntimeException ex) {
            log.warn("Could not send the verification email for user {}: {}", saved.getId(), ex.toString());
        }
        return UserResponse.from(saved);
    }

    /**
     * Sends a one-time code by SMS.
     *
     * <p>Returns the same shape whether or not anybody banks at that number; see {@link
     * VerificationChallenge}.
     */
    public VerificationChallenge requestPhoneCode(String phoneNumber, VerificationPurpose purpose) {
        return verificationService.requestPhoneCode(phoneNumber, purpose, null);
    }

    /**
     * Signs in with a phone number and a code.
     *
     * <p>The code being correct is necessary but not sufficient: the number must also belong to an
     * account that has verified it. Without that second condition, a number typed into a profile and
     * never confirmed would be a working credential for whoever actually owns the handset.
     */
    @Transactional
    public AuthenticationResponse loginWithPhone(String phoneNumber, String code) {
        VerificationCode verification =
                verificationService.verifyPhoneCode(phoneNumber, VerificationPurpose.LOGIN, code);
        User user = verification.getUser();

        if (user == null || !user.isPhoneVerified()) {
            auditService.recordIndependently(AuditEvent.failure(AuditAction.LOGIN_FAILED)
                    .by(null, PhoneNumbers.mask(PhoneNumbers.normalize(phoneNumber)))
                    .with("reason", "PHONE_NOT_REGISTERED"));
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        Instant now = clock.instant();
        if (user.isCurrentlyLockedOut(now)) {
            auditService.recordIndependently(AuditEvent.failure(AuditAction.LOGIN_FAILED)
                    .by(user)
                    .with("reason", "ACCOUNT_LOCKED"));
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_LOCKED);
        }
        if (!user.getStatus().canAuthenticate()) {
            auditService.recordIndependently(AuditEvent.failure(AuditAction.LOGIN_FAILED)
                    .by(user)
                    .with("reason", "STATUS_" + user.getStatus()));
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_NOT_ACTIVE);
        }

        // The code is single use, so a successful sign-in must not leave it standing.
        verificationService.issueTicket(verification);

        /*
         * The user is managed by this transaction, so mutating it is enough.
         *
         * Deliberately not LoginAttemptService.registerSuccess: that runs REQUIRES_NEW and would
         * commit its own copy of this row, leaving the instance here holding a stale @Version and
         * failing the flush with an optimistic lock error on an otherwise perfect sign-in. The
         * password path can use it precisely because that method is not transactional.
         */
        user.registerSuccessfulLogin(now);

        auditService.recordIndependently(
                AuditEvent.success(AuditAction.PHONE_LOGIN).by(user).on("User", user.getId()));
        auditService.recordIndependently(
                AuditEvent.success(AuditAction.LOGIN_SUCCESS).by(user).on("User", user.getId()));
        log.info("User {} authenticated by phone", user.getId());
        return issueTokens(user);
    }

    /** Checks a registration code and hands back the proof that registration will ask for. */
    @Transactional
    public VerificationTicket confirmPhoneNumber(String phoneNumber, String code) {
        VerificationCode verification =
                verificationService.verifyPhoneCode(phoneNumber, VerificationPurpose.REGISTRATION, code);
        return verificationService.issueTicket(verification);
    }

    /** Redeems an email verification link. */
    @Transactional
    public UserResponse verifyEmail(String token) {
        return UserResponse.from(verificationService.verifyEmailToken(token));
    }

    /**
     * Authenticates a user and issues a token pair.
     *
     * @throws ApiException with a deliberately generic {@link ErrorCode#AUTH_INVALID_CREDENTIALS}
     *     when either the username or the password is wrong
     */
    public AuthenticationResponse login(LoginRequest request) {
        String username = User.normalize(request.username());
        User user = findByAnyIdentifier(username);

        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            auditService.recordIndependently(AuditEvent.failure(AuditAction.LOGIN_FAILED)
                    .by(null, username)
                    .with("reason", "UNKNOWN_USERNAME"));
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        Instant now = clock.instant();
        if (user.isCurrentlyLockedOut(now)) {
            auditService.recordIndependently(AuditEvent.failure(AuditAction.LOGIN_FAILED)
                    .by(user)
                    .with("reason", "ACCOUNT_LOCKED"));
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_LOCKED);
        }
        // A lockout whose window has elapsed is treated as released; registerSuccess persists that.
        user.releaseExpiredLockout(now);

        if (!user.getStatus().canAuthenticate()) {
            auditService.recordIndependently(AuditEvent.failure(AuditAction.LOGIN_FAILED)
                    .by(user)
                    .with("reason", "STATUS_" + user.getStatus()));
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_NOT_ACTIVE);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            boolean justLocked = loginAttemptService.registerFailure(user.getId());
            auditService.recordIndependently(AuditEvent.failure(AuditAction.LOGIN_FAILED)
                    .by(user)
                    .with("reason", "BAD_PASSWORD"));
            if (justLocked) {
                auditService.recordIndependently(
                        AuditEvent.failure(AuditAction.ACCOUNT_LOCKED).by(user).on("User", user.getId()));
                throw new ApiException(ErrorCode.AUTH_ACCOUNT_LOCKED);
            }
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        loginAttemptService.registerSuccess(user.getId());
        user.registerSuccessfulLogin(now);
        auditService.recordIndependently(
                AuditEvent.success(AuditAction.PASSWORD_LOGIN).by(user).on("User", user.getId()));
        auditService.recordIndependently(AuditEvent.success(AuditAction.LOGIN_SUCCESS).by(user).on("User", user.getId()));
        log.info("User {} authenticated", user.getUsername());
        return issueTokens(user);
    }

    /**
     * Resolves whatever the customer typed into the identifier field.
     *
     * <p>People do not reliably remember which of the three they signed up with, and an interface
     * that demands the right one is failing them over bookkeeping. The order is username, then
     * email, then phone; all three are unique, so it cannot be ambiguous.
     *
     * <p>Every branch ends in the same generic failure at the call site, so this cannot be used to
     * work out which kind of identifier exists.
     */
    private User findByAnyIdentifier(String identifier) {
        java.util.Optional<User> byUsername = userRepository.findByUsername(identifier);
        if (byUsername.isPresent()) {
            return byUsername.get();
        }
        if (identifier.indexOf('@') >= 0) {
            return userRepository.findByEmail(identifier).orElse(null);
        }
        return PhoneNumbers.tryNormalize(identifier)
                .flatMap(userRepository::findByPhoneNumber)
                // Only a number the owner has actually proven is a credential.
                .filter(User::isPhoneVerified)
                .orElse(null);
    }

    /** Exchanges a valid refresh token for a new token pair, invalidating the presented one. */
    public AuthenticationResponse refresh(String refreshToken) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(refreshToken);
        User user = rotation.user();
        auditService.recordIndependently(
                AuditEvent.success(AuditAction.TOKEN_REFRESHED).by(user).on("User", user.getId()));
        JwtService.IssuedToken accessToken = jwtService.issueAccessToken(user);
        return new AuthenticationResponse(
                accessToken.value(),
                rotation.refreshToken().value(),
                AuthenticationResponse.TOKEN_TYPE,
                accessToken.expiresAt(),
                rotation.refreshToken().expiresAt(),
                UserResponse.from(user));
    }

    /** Revokes the presented refresh token and every sibling issued from the same login. */
    public void logout(String refreshToken) {
        refreshTokenService.revokeFamilyOf(refreshToken);
    }

    private AuthenticationResponse issueTokens(User user) {
        JwtService.IssuedToken accessToken = jwtService.issueAccessToken(user);
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issueNewFamily(user);
        return new AuthenticationResponse(
                accessToken.value(),
                refreshToken.value(),
                AuthenticationResponse.TOKEN_TYPE,
                accessToken.expiresAt(),
                refreshToken.expiresAt(),
                UserResponse.from(user));
    }
}
