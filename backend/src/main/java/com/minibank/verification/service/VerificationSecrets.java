package com.minibank.verification.service;

import com.minibank.common.config.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Generates and hashes verification secrets.
 *
 * <p>Hashing is HMAC-SHA256 under a server key, not BCrypt and not a bare digest, and each of those
 * choices is deliberate:
 *
 * <ul>
 *   <li>A <b>bare SHA-256</b> would be useless. Six digits is a million possibilities; anyone
 *       holding the database could precompute every one of them in a second.
 *   <li><b>BCrypt</b> would be the right answer for a password, whose plaintext a user chooses and
 *       reuses. It is the wrong answer here: verifying a code would cost tens of milliseconds of
 *       deliberate work on an endpoint that is meant to be cheap, and the code's protection comes
 *       from living five minutes and allowing five guesses, not from being slow to test.
 *   <li><b>Keyed HMAC</b> gives what is actually needed: the stored value is worthless without a
 *       key that is not in the database, and checking it costs nothing.
 * </ul>
 *
 * <p>Comparison is constant time, so the endpoint cannot be turned into an oracle that leaks a code
 * one digit at a time through response timing.
 */
@Component
public final class VerificationSecrets {

    private static final String MAC = "HmacSHA256";
    private static final String LABEL = "mbank:verification:secret:v1";

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public VerificationSecrets(SecurityProperties securityProperties) {
        // Derived from the JWT secret rather than configured separately: one mandatory secret is one
        // fewer thing to forget, and the label keeps this key unrelated to the signing key itself.
        this.key = new SecretKeySpec(
                hmac(
                        new SecretKeySpec(securityProperties.jwt().secret().getBytes(StandardCharsets.UTF_8), MAC),
                        LABEL),
                MAC);
    }

    /**
     * A numeric one-time code of the requested length.
     *
     * <p>Rejection sampling rather than {@code nextInt() % 10}: the modulo of a uniformly random
     * byte over ten is biased towards the low digits, and a biased OTP is a smaller search space
     * than it appears to be.
     */
    public String numericCode(int digits) {
        StringBuilder code = new StringBuilder(digits);
        for (int i = 0; i < digits; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    /** A URL-safe token with 256 bits behind it, for links. */
    public String urlToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** The value stored in place of a secret. */
    public String hash(String secret) {
        return HexFormat.of().formatHex(hmac(key, secret));
    }

    /** Constant-time check of a presented secret against a stored hash. */
    public boolean matches(String presented, String storedHash) {
        if (presented == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(presented).getBytes(StandardCharsets.UTF_8), storedHash.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(SecretKeySpec macKey, String material) {
        try {
            Mac mac = Mac.getInstance(MAC);
            mac.init(macKey);
            return mac.doFinal(material.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC computation failed", ex);
        }
    }
}
