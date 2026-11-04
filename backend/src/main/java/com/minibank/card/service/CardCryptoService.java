package com.minibank.card.service;

import com.minibank.card.config.CardProperties;
import com.minibank.card.domain.Card;
import com.minibank.common.config.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Everything cryptographic about a card number.
 *
 * <p>Three operations, three different keys, all derived from one root secret so that a compromise
 * of one purpose cannot be replayed against another:
 *
 * <ul>
 *   <li><b>Encryption</b> — AES-256-GCM over the full number. GCM rather than CBC because it
 *       authenticates as well as encrypts: a tampered ciphertext fails to decrypt instead of
 *       yielding a different valid-looking number. A fresh random IV is generated per card and
 *       stored in front of the ciphertext, which is what makes two identical numbers encrypt to
 *       different bytes.
 *   <li><b>Fingerprinting</b> — HMAC-SHA256, so "has this number been issued before" can be
 *       answered by an indexed equality check without the plaintext existing anywhere to compare
 *       against. A plain SHA-256 would be brute-forceable: the space of card numbers is small
 *       enough to enumerate, and the key is what closes that.
 *   <li><b>Verification value</b> — HMAC-SHA256 over the number, expiry and card id, reduced to
 *       three digits. It is <em>derived</em>, never stored, so there is no CVV column to leak and
 *       nothing to steal from a database dump. This is the same shape as an issuer deriving CVV
 *       from a card verification key, and it is why {@code cards} has no such column.
 * </ul>
 *
 * <p>The key material never leaves this class, and no method here logs its arguments.
 */
@Service
public final class CardCryptoService {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final String MAC = "HmacSHA256";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    /** Domain separation: the same root secret produces three unrelated keys. */
    private static final String ENCRYPTION_LABEL = "mbank:card:pan-encryption:v1";
    private static final String FINGERPRINT_LABEL = "mbank:card:pan-fingerprint:v1";
    private static final String CVV_LABEL = "mbank:card:verification-value:v1";

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec fingerprintKey;
    private final SecretKeySpec verificationKey;

    public CardCryptoService(CardProperties cardProperties, SecurityProperties securityProperties) {
        byte[] root = rootSecret(cardProperties, securityProperties);
        this.encryptionKey = new SecretKeySpec(derive(root, ENCRYPTION_LABEL), "AES");
        this.fingerprintKey = new SecretKeySpec(derive(root, FINGERPRINT_LABEL), MAC);
        this.verificationKey = new SecretKeySpec(derive(root, CVV_LABEL), MAC);
    }

    /** Encrypts a freshly generated number into the form the entity stores. */
    public Card.EncryptedPan protect(String pan) {
        String digits = pan.replaceAll("\\D", "");
        return new Card.EncryptedPan(
                digits.substring(0, 6),
                digits.substring(digits.length() - 4),
                encrypt(digits),
                fingerprint(digits));
    }

    /**
     * Recovers the full number.
     *
     * <p>Called from exactly one place — the audited, rate-limited reveal endpoint. Every other read
     * path uses the stored last four digits.
     */
    public String revealPan(Card card) {
        return decrypt(card.getPanCiphertext());
    }

    /**
     * Derives the card's three-digit verification value.
     *
     * <p>Deterministic for a given card and stable for its lifetime, but computable only with the
     * server key, so it behaves like a stored value without ever being one.
     */
    public String verificationValue(Card card, String pan) {
        String material = "%s|%s|%02d%02d"
                .formatted(card.getId(), pan, card.getExpirationMonth(), card.getExpirationYear() % 100);
        byte[] digest = hmac(verificationKey, material);
        // Three digits from the leading 24 bits, masked to stay positive.
        int value = ((digest[0] & 0x7F) << 16 | (digest[1] & 0xFF) << 8 | (digest[2] & 0xFF)) % 1000;
        return "%03d".formatted(value);
    }

    private String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] envelope = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, envelope, 0, iv.length);
            System.arraycopy(ciphertext, 0, envelope, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(envelope);
        } catch (GeneralSecurityException ex) {
            // The message deliberately carries no plaintext and no key material.
            throw new IllegalStateException("Card number could not be encrypted", ex);
        }
    }

    private String decrypt(String envelope) {
        try {
            byte[] raw = Base64.getDecoder().decode(envelope);
            byte[] iv = java.util.Arrays.copyOfRange(raw, 0, IV_BYTES);
            byte[] ciphertext = java.util.Arrays.copyOfRange(raw, IV_BYTES, raw.length);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Stored card number could not be decrypted", ex);
        }
    }

    private String fingerprint(String pan) {
        return HexFormat.of().formatHex(hmac(fingerprintKey, pan));
    }

    private static byte[] hmac(SecretKeySpec key, String material) {
        try {
            Mac mac = Mac.getInstance(MAC);
            mac.init(key);
            return mac.doFinal(material.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC computation failed", ex);
        }
    }

    /**
     * The root the three keys are derived from: the configured card key when one is set, and
     * otherwise the JWT secret, which is already required to start the application.
     */
    private static byte[] rootSecret(CardProperties cardProperties, SecurityProperties securityProperties) {
        String configured = cardProperties.encryptionKey();
        if (configured != null && !configured.isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(configured.trim());
            if (decoded.length < KEY_BYTES) {
                throw new IllegalStateException(
                        "minibank.cards.encryption-key must decode to at least %d bytes".formatted(KEY_BYTES));
            }
            return decoded;
        }
        return securityProperties.jwt().secret().getBytes(StandardCharsets.UTF_8);
    }

    /** One-step HKDF-style expansion: an HMAC of a fixed label under the root secret. */
    private static byte[] derive(byte[] root, String label) {
        byte[] full = hmac(new SecretKeySpec(root, MAC), label);
        return java.util.Arrays.copyOf(full, KEY_BYTES);
    }
}
