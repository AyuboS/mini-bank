package com.minibank.card.domain;

/** The lifecycle of a card. */
public enum CardStatus {

    ACTIVE,

    /** Temporarily blocked by the cardholder. Reversible, unlike {@link #CANCELLED}. */
    FROZEN,

    /** Past its expiry date. Derived from the date rather than set by a user. */
    EXPIRED,

    /** Terminal. A cancelled card is never reactivated; the customer orders a new one. */
    CANCELLED;

    /** True while the card could still be used to spend money. */
    public boolean isSpendable() {
        return this == ACTIVE;
    }

    /** True when the status can still change, i.e. the card has not reached a terminal state. */
    public boolean isMutable() {
        return this == ACTIVE || this == FROZEN;
    }
}
