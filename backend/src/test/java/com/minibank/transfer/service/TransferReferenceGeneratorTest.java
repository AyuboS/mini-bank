package com.minibank.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransferReferenceGeneratorTest {

    private final TransferReferenceGenerator generator =
            new TransferReferenceGenerator(Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void producesTheDocumentedFormat() {
        assertThat(generator.generate()).matches("^TRX-2026-[A-Z2-9]{8}$");
    }

    @Test
    void omitsCharactersThatAreAmbiguousWhenReadAloud() {
        // I/O/0/1 are excluded so a reference dictated over the phone survives the trip.
        for (int i = 0; i < 200; i++) {
            assertThat(generator.generate().substring(9)).doesNotContain("I", "O", "0", "1");
        }
    }

    @Test
    void producesDistinctReferences() {
        Set<String> references = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            references.add(generator.generate());
        }
        assertThat(references).hasSize(1000);
    }
}
