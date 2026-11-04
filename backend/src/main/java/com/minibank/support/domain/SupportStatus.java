package com.minibank.support.domain;

/** Where a ticket is in its life. */
public enum SupportStatus {
    /** With the bank. */
    OPEN,
    /** With the customer: the bank has asked something and is waiting. */
    AWAITING_CUSTOMER,
    /** Answered. The customer can still reply, which reopens it. */
    RESOLVED,
    /** Closed for good. A reply starts a new ticket. */
    CLOSED;

    public boolean acceptsReplies() {
        return this != CLOSED;
    }
}
