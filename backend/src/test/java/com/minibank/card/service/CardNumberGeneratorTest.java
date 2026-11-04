package com.minibank.card.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.minibank.card.domain.CardNetwork;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CardNumberGeneratorTest {

    private final CardNumberGenerator generator = new CardNumberGenerator();

    @ParameterizedTest
    @EnumSource(CardNetwork.class)
    @DisplayName("a generated number is sixteen digits and passes the Luhn check")
    void generatesLuhnValidSixteenDigitNumbers(CardNetwork network) {
        for (int i = 0; i < 200; i++) {
            String number = generator.generate(network);
            assertThat(number).hasSize(16).containsOnlyDigits();
            assertThat(CardNumberGenerator.isLuhnValid(number))
                    .as("Luhn check for %s", number)
                    .isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(CardNetwork.class)
    @DisplayName("the number starts with the scheme's own prefix")
    void carriesTheSchemePrefix(CardNetwork network) {
        assertThat(generator.generate(network)).startsWith(network.issuerPrefix());
    }

    @Test
    @DisplayName("numbers do not repeat across a large batch")
    void doesNotRepeat() {
        Set<String> seen = new HashSet<>();
        IntStream.range(0, 2000).forEach(i -> seen.add(generator.generate(CardNetwork.VISA)));
        assertThat(seen).hasSize(2000);
    }

    @Test
    @DisplayName("a number with a digit changed fails the check")
    void detectsATamperedDigit() {
        String number = generator.generate(CardNetwork.VISA);
        char digit = number.charAt(5);
        String tampered = number.substring(0, 5) + (digit == '9' ? '0' : (char) (digit + 1)) + number.substring(6);
        assertThat(CardNumberGenerator.isLuhnValid(tampered)).isFalse();
    }
}
