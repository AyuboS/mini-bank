package com.minibank.ledger.domain;

/** The two sides of a double-entry posting. */
public enum EntryType {
    /** Money leaving an account. */
    DEBIT,
    /** Money arriving in an account. */
    CREDIT;

    /** The signed contribution this entry makes to an account's balance. */
    public java.math.BigDecimal signedAmount(java.math.BigDecimal amount) {
        return this == CREDIT ? amount : amount.negate();
    }
}
