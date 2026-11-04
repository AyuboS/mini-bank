package com.minibank.verification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minibank.common.error.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumbersTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "+998901234567",
                "+998 90 123 45 67",
                "+998 (90) 123-45-67",
                "998901234567",
                "00998901234567",
                "  +998-90-123.45.67  "
            })
    @DisplayName("every way of writing one number produces the same E.164 string")
    void normalizesEquivalentFormsIdentically(String input) {
        assertThat(PhoneNumbers.normalize(input)).isEqualTo("+998901234567");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "abcdefgh", "+0123456789", "12345", "+", "++998901234567"})
    @DisplayName("anything that cannot be a number is rejected rather than guessed at")
    void rejectsUnusableInput(String input) {
        assertThatThrownBy(() -> PhoneNumbers.normalize(input)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("null is rejected")
    void rejectsNull() {
        assertThatThrownBy(() -> PhoneNumbers.normalize(null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("tryNormalize reports failure instead of throwing")
    void tryNormalizeIsQuiet() {
        assertThat(PhoneNumbers.tryNormalize("nonsense")).isEmpty();
        assertThat(PhoneNumbers.tryNormalize("+998901234567")).contains("+998901234567");
    }

    @Test
    @DisplayName("a masked number shows the country and the last two digits, and nothing between")
    void masksTheMiddle() {
        String masked = PhoneNumbers.mask("+998901234567");
        assertThat(masked).startsWith("+998").endsWith("67").doesNotContain("901234");
    }

    @Test
    @DisplayName("masking a value too short to mask does not leak it")
    void masksShortInputSafely() {
        assertThat(PhoneNumbers.mask("+12")).isEqualTo("•••");
        assertThat(PhoneNumbers.mask(null)).isEqualTo("•••");
    }
}
