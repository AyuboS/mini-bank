package com.minibank.common.money;

import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Helpers for monetary arithmetic.
 *
 * <p>Money is always a {@link BigDecimal}; {@code double} and {@code float} cannot represent values
 * such as 0.10 exactly, and repeated addition of inexact values silently drifts. Amounts are stored
 * at {@link #STORAGE_SCALE} so that intermediate values (for example future interest or fee
 * calculations) keep headroom below the minor unit.
 *
 * <p>Rounding is never applied to a client-supplied amount. A request carrying more decimals than
 * the currency supports is rejected, because quietly rounding somebody's money is a defect, not a
 * convenience.
 */
public final class Money {

    /** Scale used by the {@code NUMERIC(19, 4)} columns that hold balances and amounts. */
    public static final int STORAGE_SCALE = 4;

    private Money() {}

    /** Returns {@code amount} normalised to the storage scale, without changing its value. */
    public static BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(STORAGE_SCALE, RoundingMode.UNNECESSARY);
    }

    /** Zero, at storage scale. */
    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(STORAGE_SCALE, RoundingMode.UNNECESSARY);
    }

    /**
     * Validates that a client supplied amount is positive and expressed in whole minor units of the
     * given currency.
     *
     * @throws BusinessRuleException if the amount is null, non-positive, or too precise
     */
    public static BigDecimal requireValidAmount(BigDecimal amount, CurrencyCode currency) {
        if (amount == null) {
            throw new BusinessRuleException(ErrorCode.INVALID_AMOUNT, "Amount is required");
        }
        if (amount.signum() <= 0) {
            throw new BusinessRuleException(ErrorCode.INVALID_AMOUNT, "Amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > currency.minorUnitScale()) {
            throw new BusinessRuleException(
                    ErrorCode.INVALID_AMOUNT,
                    "Amount for %s supports at most %d decimal places"
                            .formatted(currency, currency.minorUnitScale()));
        }
        return normalize(amount);
    }

    /** True when {@code balance} is greater than or equal to {@code amount}. */
    public static boolean isSufficient(BigDecimal balance, BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    /** Value equality that ignores scale, unlike {@link BigDecimal#equals(Object)}. */
    public static boolean equal(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }
}
