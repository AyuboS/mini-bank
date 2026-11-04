package com.minibank.support.domain;

/** What a ticket is about. Kept short so the customer picks one rather than reading a menu. */
public enum SupportCategory {
    CARD,
    TRANSFER,
    ACCOUNT,
    SECURITY,
    /** The app itself: something looks wrong, something will not load. */
    APP,
    OTHER
}
