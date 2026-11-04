package com.minibank.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    @ParameterizedTest(name = "accepts {0}")
    @ValueSource(strings = {"StrongPassword123!", "Aa1!aaaaaaaa", "Correct-Horse-9-Battery"})
    void acceptsPasswordsMeetingEveryRule(String password) {
        assertThat(PasswordPolicy.isValid(password)).isTrue();
    }

    @ParameterizedTest(name = "rejects {1}")
    @CsvSource({
        "'Sh0rt!aa', too short",
        "'alllowercase1!', no upper case",
        "'ALLUPPERCASE1!', no lower case",
        "'NoDigitsHere!!', no digit",
        "'NoSpecialChar123', no special character"
    })
    void rejectsPasswordsMissingARequirement(String password, String reason) {
        assertThat(PasswordPolicy.isValid(password)).as(reason).isFalse();
    }

    @Test
    void rejectsNull() {
        assertThat(PasswordPolicy.isValid(null)).isFalse();
    }

    @Test
    void rejectsPasswordsBeyondTheBcryptInputLimit() {
        // BCrypt silently truncates past 72 bytes; accepting more would overstate the strength.
        String tooLong = "Aa1!" + "x".repeat(PasswordPolicy.MAX_LENGTH);
        assertThat(PasswordPolicy.isValid(tooLong)).isFalse();
    }

    @Test
    void acceptsExactlyTheMaximumLength() {
        String atLimit = "Aa1!" + "x".repeat(PasswordPolicy.MAX_LENGTH - 4);
        assertThat(atLimit).hasSize(PasswordPolicy.MAX_LENGTH);
        assertThat(PasswordPolicy.isValid(atLimit)).isTrue();
    }
}
