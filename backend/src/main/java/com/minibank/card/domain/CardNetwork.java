package com.minibank.card.domain;

/**
 * The card scheme a card is presented as.
 *
 * <p>MBank is not connected to any payment processor and has no relationship with any scheme. This
 * is a presentational attribute of demo cards, and the numbers behind it are generated locally; see
 * {@link com.minibank.card.service.CardNumberGenerator}.
 */
public enum CardNetwork {

    /** Real Visa numbers begin with 4. */
    VISA("4"),

    /** Real Mastercard numbers begin 51-55 or 2221-2720. */
    MASTERCARD("52");

    private final String issuerPrefix;

    CardNetwork(String issuerPrefix) {
        this.issuerPrefix = issuerPrefix;
    }

    /**
     * The leading digits a demo number of this scheme carries, reproduced so that the number is
     * recognisable as the scheme it claims to be.
     */
    public String issuerPrefix() {
        return issuerPrefix;
    }
}
