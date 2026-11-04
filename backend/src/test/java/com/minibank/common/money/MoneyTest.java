package com.minibank.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    @Nested
    @DisplayName("requireValidAmount")
    class RequireValidAmount {

        @Test
        void acceptsAPositiveAmountWithinTheCurrencyScale() {
            BigDecimal result = Money.requireValidAmount(new BigDecimal("100.50"), CurrencyCode.EUR);

            assertThat(result).isEqualByComparingTo("100.50");
            assertThat(result.scale()).isEqualTo(Money.STORAGE_SCALE);
        }

        @Test
        void acceptsAWholeNumber() {
            assertThat(Money.requireValidAmount(new BigDecimal("40"), CurrencyCode.EUR))
                    .isEqualByComparingTo("40");
        }

        @Test
        void acceptsTrailingZerosBeyondTheCurrencyScale() {
            // 100.5000 carries no more information than 100.50, so it is not over-precise.
            assertThat(Money.requireValidAmount(new BigDecimal("100.5000"), CurrencyCode.EUR))
                    .isEqualByComparingTo("100.50");
        }

        @Test
        void rejectsNull() {
            assertThatThrownBy(() -> Money.requireValidAmount(null, CurrencyCode.EUR))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(ex -> ((BusinessRuleException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_AMOUNT);
        }

        @ParameterizedTest(name = "rejects {0}")
        @ValueSource(strings = {"0", "0.00", "-1", "-0.01", "-100.50"})
        void rejectsNonPositiveAmounts(String amount) {
            assertThatThrownBy(() -> Money.requireValidAmount(new BigDecimal(amount), CurrencyCode.EUR))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("greater than zero");
        }

        @Test
        void rejectsMorePrecisionThanTheCurrencySupports() {
            // Rounding somebody's money silently is a defect, so a third decimal is refused.
            assertThatThrownBy(() -> Money.requireValidAmount(new BigDecimal("10.005"), CurrencyCode.EUR))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("at most 2 decimal places");
        }
    }

    @Test
    void normalizePreservesValueAndFixesScale() {
        assertThat(Money.normalize(new BigDecimal("10.5")))
                .isEqualByComparingTo("10.5")
                .hasToString("10.5000");
    }

    @Test
    void zeroIsAtStorageScale() {
        assertThat(Money.zero()).isEqualByComparingTo("0").hasToString("0.0000");
    }

    @Test
    void isSufficientComparesByValueNotScale() {
        assertThat(Money.isSufficient(new BigDecimal("100.0000"), new BigDecimal("100"))).isTrue();
        assertThat(Money.isSufficient(new BigDecimal("99.99"), new BigDecimal("100"))).isFalse();
    }

    @Test
    void equalIgnoresScaleUnlikeBigDecimalEquals() {
        BigDecimal a = new BigDecimal("100.0");
        BigDecimal b = new BigDecimal("100.00");

        assertThat(a.equals(b)).isFalse();
        assertThat(Money.equal(a, b)).isTrue();
    }

    @Test
    void repeatedAdditionStaysExact() {
        // The reason money is not a double: 0.1 has no exact binary representation, so this loop
        // would drift with floating point and land on 0.9999999999999999.
        BigDecimal total = Money.zero();
        for (int i = 0; i < 10; i++) {
            total = total.add(new BigDecimal("0.10"));
        }
        assertThat(total).isEqualByComparingTo("1.00");
    }
}
