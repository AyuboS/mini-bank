package com.minibank.transfer.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/**
 * Produces human-quotable transfer references of the form {@code TRX-2026-A7F3K92M}.
 *
 * <p>The year makes a reference easy to place when a customer reads it out on the phone; the random
 * suffix (uppercase alphanumerics, ambiguous characters removed) keeps references unguessable.
 * Uniqueness is guaranteed by the {@code uq_transfers_reference} constraint, not by this class.
 */
@Component
public class TransferReferenceGenerator {

    /** No I, O, 0 or 1: they are indistinguishable when a reference is read aloud or handwritten. */
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int SUFFIX_LENGTH = 8;

    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public TransferReferenceGenerator(Clock clock) {
        this.clock = clock;
    }

    public String generate() {
        StringBuilder builder = new StringBuilder(17)
                .append("TRX-")
                .append(clock.instant().atZone(ZoneOffset.UTC).getYear())
                .append('-');
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            builder.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }
}
