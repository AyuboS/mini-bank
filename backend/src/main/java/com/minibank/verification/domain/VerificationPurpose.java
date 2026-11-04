package com.minibank.verification.domain;

/**
 * Why a verification was started.
 *
 * <p>The purpose is fixed when the code is requested and checked again when it is used, so a code
 * obtained for one flow cannot be redeemed in another. Without it, a code sent to "confirm this is
 * your number" would also be a working login credential.
 */
public enum VerificationPurpose {

    /** Signing in with a phone number instead of a password. */
    LOGIN,

    /** Proving control of a contact before an account is created for it. */
    REGISTRATION,

    /** Attaching or confirming a contact on an account that already exists. */
    CONTACT_VERIFICATION,

    /**
     * Proving control of a contact in order to set a new password.
     *
     * <p>Separate from {@link #LOGIN} on purpose. Because the purpose is bound to the code when it
     * is issued and checked again when it is redeemed, a reset code cannot be turned into a
     * sign-in, and a sign-in code cannot be turned into a password change.
     */
    PASSWORD_RESET
}
