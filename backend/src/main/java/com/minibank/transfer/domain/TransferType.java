package com.minibank.transfer.domain;

/** What kind of money movement a transfer represents. */
public enum TransferType {
    /** Customer to customer, within the bank. */
    INTERNAL,
    /** Money entering the bank: system funding account to a customer account. */
    DEPOSIT,
    /** Money leaving the bank: customer account to the system funding account. */
    WITHDRAWAL,
    /** A compensating movement that undoes an earlier settled transfer. */
    REVERSAL
}
