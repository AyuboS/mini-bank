package com.minibank.card.service;

import com.minibank.card.domain.CardNetwork;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Produces the 16-digit numbers MBank issues to demo cards.
 *
 * <p>These are not payment credentials. MBank has no processor, no scheme membership and no BIN
 * allocation; the numbers exist so that a card in the interface looks like a card. They are given a
 * scheme-appropriate leading digit and a valid Luhn check digit, because a number that fails the
 * check every payment form in the world performs would look broken rather than realistic.
 *
 * <p>The middle digits come from {@link SecureRandom} rather than {@code Math.random()}: the number
 * is treated as a secret from the moment it is created, and a predictable generator would make the
 * whole encryption story downstream pointless.
 */
@Component
public class CardNumberGenerator {

    private static final int LENGTH = 16;

    /**
     * Fixed digits following the scheme prefix, so every MBank demo number shares a recognisable
     * six-digit BIN and none of them can collide with a real issuer's range by accident.
     */
    private static final String MBANK_ISSUER_DIGITS = "84";

    private final SecureRandom random = new SecureRandom();

    /** A Luhn-valid 16-digit number for the given scheme. */
    public String generate(CardNetwork network) {
        StringBuilder digits = new StringBuilder(LENGTH);
        digits.append(network.issuerPrefix()).append(MBANK_ISSUER_DIGITS);

        while (digits.length() < LENGTH - 1) {
            digits.append(random.nextInt(10));
        }
        digits.append(luhnCheckDigit(digits));
        return digits.toString();
    }

    /**
     * The digit that makes the whole number satisfy the Luhn checksum.
     *
     * <p>Doubling runs from the right-hand end of the finished number, so with the check digit not
     * yet appended the rightmost existing digit is the one that gets doubled.
     */
    static int luhnCheckDigit(CharSequence withoutCheckDigit) {
        int sum = 0;
        boolean doubling = true;

        for (int i = withoutCheckDigit.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(withoutCheckDigit.charAt(i));
            if (doubling) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubling = !doubling;
        }
        return (10 - (sum % 10)) % 10;
    }

    /** True when a complete number satisfies the Luhn checksum. Used by the tests. */
    public static boolean isLuhnValid(String number) {
        int sum = 0;
        boolean doubling = false;

        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(number.charAt(i));
            if (doubling) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubling = !doubling;
        }
        return sum % 10 == 0;
    }
}
