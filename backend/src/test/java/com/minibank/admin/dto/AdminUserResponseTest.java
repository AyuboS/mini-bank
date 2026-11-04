package com.minibank.admin.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AdminUserResponseTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        // Formatting is discarded and every digit but the last four is starred out, so the
        // masked value cannot be used to reconstruct the original number.
        "'+49 151 12345678', '*********5678'",
        "'0044 7700 900123', '**********0123'",
        "'1234', '****'",
        "'12', '**'"
    })
    void masksAllButTheLastFourDigits(String input, String expected) {
        assertThat(AdminUserResponse.mask(input)).isEqualTo(expected);
    }

    @Test
    void keepsTheLastFourDigitsVisibleForCustomerSupport() {
        assertThat(AdminUserResponse.mask("+49 151 12345678")).endsWith("5678");
    }

    @Test
    void handlesAbsentPhoneNumbers() {
        assertThat(AdminUserResponse.mask(null)).isNull();
        assertThat(AdminUserResponse.mask("   ")).isNull();
    }
}
