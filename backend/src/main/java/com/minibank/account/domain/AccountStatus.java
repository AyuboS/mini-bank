package com.minibank.account.domain;

/** Lifecycle state of a bank account. */
public enum AccountStatus {
    /** Can send and receive money. */
    ACTIVE,
    /** Administratively frozen: cannot send or receive money, balance is preserved. */
    FROZEN,
    /** Permanently closed. Retained for audit; cannot send or receive money. */
    CLOSED;

    public boolean canTransact() {
        return this == ACTIVE;
    }
}
