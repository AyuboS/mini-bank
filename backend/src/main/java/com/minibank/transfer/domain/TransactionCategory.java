package com.minibank.transfer.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * What a payment was for.
 *
 * <p>Thirteen buckets, chosen to be exhaustive without being a taxonomy: a customer classifying a
 * month of spending should never have to think for more than a second, and every extra category
 * makes the analytics page harder to read rather than more precise.
 *
 * <p>{@link #SALARY} and {@link #TRANSFER} are structural rather than discretionary, and {@link
 * #isSpending()} exists so that the analytics never present "money I moved between my own accounts"
 * as money I spent. That single distinction is the difference between a spending chart that is
 * useful and one that is merely arithmetic.
 */
public enum TransactionCategory {
    GROCERIES("Groceries", true),
    DINING("Dining", true),
    SHOPPING("Shopping", true),
    TRANSPORT("Transport", true),
    BILLS("Bills", true),
    ENTERTAINMENT("Entertainment", true),
    TRAVEL("Travel", true),
    HEALTH("Health", true),
    EDUCATION("Education", true),

    /** Money arriving as pay. Never spending, by definition. */
    SALARY("Salary", false),
    /** Money moved into a savings goal. Not consumption: it is still the customer's. */
    SAVINGS("Savings", false),
    /** Movement between accounts or to another person, with no consumption implied. */
    TRANSFER("Transfer", false),

    OTHER("Other", true);

    private final String displayName;
    private final boolean spending;

    TransactionCategory(String displayName, boolean spending) {
        this.displayName = displayName;
        this.spending = spending;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * Whether money in this category left the customer's control.
     *
     * <p>Used by the analytics to separate a spending total from a movement total. A €500 payment
     * into a savings goal is not a €500 expense, and a chart that says otherwise teaches the
     * customer to distrust the whole page.
     */
    public boolean isSpending() {
        return spending;
    }

    /** Parses a client supplied name leniently, returning empty rather than throwing. */
    public static Optional<TransactionCategory> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
