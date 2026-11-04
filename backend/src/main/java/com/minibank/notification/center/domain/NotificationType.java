package com.minibank.notification.center.domain;

/** What part of the product a notification is about. Drives grouping and the customer's filters. */
public enum NotificationType {
    /** Money moved: a payment sent, received, held or refused. */
    TRANSACTION,
    /** Sign-ins, sessions, password and device changes. */
    SECURITY,
    /** Issuing, freezing, limits and card controls. */
    CARD,
    /** A spending budget approaching or passing its cap. */
    BUDGET,
    /** Everything the bank itself has to say. */
    SYSTEM
}
