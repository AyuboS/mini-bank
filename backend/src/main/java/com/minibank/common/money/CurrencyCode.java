package com.minibank.common.money;

/**
 * Currencies supported by MBank.
 *
 * <p>Every supported currency happens to use two minor-unit digits, but the scale is modelled
 * explicitly rather than assumed, so that adding a zero-decimal currency such as JPY later is a
 * data change rather than a hunt through arithmetic code.
 */
public enum CurrencyCode {
    EUR("€", 2),
    USD("$", 2),
    GBP("£", 2);

    private final String symbol;
    private final int minorUnitScale;

    CurrencyCode(String symbol, int minorUnitScale) {
        this.symbol = symbol;
        this.minorUnitScale = minorUnitScale;
    }

    public String symbol() {
        return symbol;
    }

    /** Number of decimal digits that are meaningful for this currency. */
    public int minorUnitScale() {
        return minorUnitScale;
    }
}
