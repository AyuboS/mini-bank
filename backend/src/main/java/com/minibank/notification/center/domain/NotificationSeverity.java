package com.minibank.notification.center.domain;

/**
 * How loudly a notification should present itself.
 *
 * <p>Separate from {@link NotificationType} because the two are genuinely independent: a card
 * notification can be routine ("your new card is ready") or urgent ("your card was used abroad"),
 * and collapsing the two axes would force one of those to be mislabelled.
 */
public enum NotificationSeverity {
    INFO,
    SUCCESS,
    WARNING,
    /** Needs attention now. Reserved for security events and refused payments. */
    CRITICAL
}
