package com.minibank.user.domain;

/** Lifecycle state of a user account. */
public enum UserStatus {
    /** Normal, fully usable account. */
    ACTIVE,
    /** Temporarily locked after repeated failed logins. Unlocks automatically. */
    LOCKED,
    /** Administratively suspended. Only an administrator can restore it. */
    SUSPENDED,
    /** Soft deleted. Financial history is retained; the account can no longer authenticate. */
    DELETED;

    public boolean canAuthenticate() {
        return this == ACTIVE;
    }
}
