package com.minibank.account.domain;

/** Distinguishes customer-owned accounts from the bank's own internal accounts. */
public enum AccountType {
    /** Owned by a customer. May never hold a negative balance. */
    CUSTOMER,
    /**
     * The bank's own funding position, one per currency. Acts as the counterparty for deposits and
     * withdrawals so that every credit has a matching debit, and is therefore allowed to go
     * negative: a negative system balance simply means the bank owes its customers that much.
     */
    SYSTEM;

    public boolean allowsNegativeBalance() {
        return this == SYSTEM;
    }
}
