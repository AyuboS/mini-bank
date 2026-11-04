package com.minibank.moneyrequest.domain;

/** Where a payment request stands. */
public enum MoneyRequestStatus {
    /** Sent, and still awaiting the payer. */
    PENDING,
    /** The payer settled it. The transfer that did so is recorded against the request. */
    PAID,
    /** The payer said no. */
    DECLINED,
    /** Nobody acted before the expiry date. */
    EXPIRED,
    /** Withdrawn by the person who asked. */
    CANCELLED;

    public boolean isSettled() {
        return this != PENDING;
    }
}
