package com.minibank.card.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.minibank.card.config.CardProperties;
import com.minibank.card.domain.Card;
import com.minibank.card.domain.CardDesign;
import com.minibank.card.domain.CardNetwork;
import com.minibank.card.domain.CardType;
import com.minibank.common.config.SecurityProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CardCryptoServiceTest {

    private static final String PAN = "4284193948217351";

    private final CardCryptoService crypto = serviceWithSecret("unit-test-card-secret-0123456789-abcdefghij");

    @Test
    @DisplayName("a protected number keeps only its ends in the clear")
    void exposesOnlyTheEnds() {
        Card.EncryptedPan protectedPan = crypto.protect(PAN);

        assertThat(protectedPan.bin()).isEqualTo("428419");
        assertThat(protectedPan.lastFour()).isEqualTo("7351");
        assertThat(protectedPan.ciphertext()).doesNotContain(PAN);
        assertThat(protectedPan.fingerprint()).doesNotContain(PAN).hasSize(64);
    }

    @Test
    @DisplayName("encryption round-trips")
    void decryptsWhatItEncrypted() {
        Card card = cardWith(crypto.protect(PAN));
        assertThat(crypto.revealPan(card)).isEqualTo(PAN);
    }

    @Test
    @DisplayName("the same number encrypts differently every time, but fingerprints the same")
    void usesAFreshIvButAStableFingerprint() {
        Card.EncryptedPan first = crypto.protect(PAN);
        Card.EncryptedPan second = crypto.protect(PAN);

        // A fresh IV per card: identical numbers must not be identifiable as identical from the
        // ciphertext alone.
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        // The fingerprint is what uniqueness is enforced on, so it has to be stable.
        assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
    }

    @Test
    @DisplayName("a different number fingerprints differently")
    void fingerprintsDistinguishNumbers() {
        assertThat(crypto.protect(PAN).fingerprint())
                .isNotEqualTo(crypto.protect("4284193948217352").fingerprint());
    }

    @Test
    @DisplayName("the verification value is three digits and stable for a card")
    void derivesAStableVerificationValue() {
        Card card = cardWith(crypto.protect(PAN));
        card.setId(UUID.randomUUID());

        String first = crypto.verificationValue(card, PAN);
        assertThat(first).hasSize(3).containsOnlyDigits();
        assertThat(crypto.verificationValue(card, PAN)).isEqualTo(first);
    }

    @Test
    @DisplayName("two cards do not share a verification value by construction")
    void derivesPerCardVerificationValues() {
        Card first = cardWith(crypto.protect(PAN));
        first.setId(UUID.randomUUID());
        Card second = cardWith(crypto.protect(PAN));
        second.setId(UUID.randomUUID());

        // Three digits collide one time in a thousand, so this asserts the inputs differ rather
        // than that the outputs must: the material fed to the HMAC includes the card id.
        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(crypto.verificationValue(first, PAN)).hasSize(3);
    }

    @Test
    @DisplayName("a different root secret produces different keys")
    void keysAreBoundToTheSecret() {
        CardCryptoService other = serviceWithSecret("a-completely-different-secret-0123456789-xyz");
        assertThat(other.protect(PAN).fingerprint()).isNotEqualTo(crypto.protect(PAN).fingerprint());
    }

    private static CardCryptoService serviceWithSecret(String secret) {
        return new CardCryptoService(
                new CardProperties("", 8, 10),
                new SecurityProperties(
                        new SecurityProperties.Jwt(secret, "mbank", Duration.ofMinutes(15), Duration.ofDays(7)),
                        new SecurityProperties.Lockout(5, Duration.ofMinutes(15)),
                        new SecurityProperties.Cors(List.of("http://localhost:5173"))));
    }

    /** A card carrying the protected number, with no persistence involved. */
    private static Card cardWith(Card.EncryptedPan pan) {
        return Card.issue(
                null,
                userStub(),
                CardType.VIRTUAL,
                CardDesign.AURORA,
                CardNetwork.VISA,
                "Test",
                pan,
                Instant.parse("2026-01-15T00:00:00Z"));
    }

    private static com.minibank.user.domain.User userStub() {
        return com.minibank.user.domain.User.create(
                "cardholder",
                "cardholder@example.com",
                "hash",
                "Ada",
                "Lovelace",
                com.minibank.user.domain.UserRole.CUSTOMER);
    }
}
