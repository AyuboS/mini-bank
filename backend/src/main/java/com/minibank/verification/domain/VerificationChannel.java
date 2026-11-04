package com.minibank.verification.domain;

/**
 * How a verification secret reaches its owner.
 *
 * <p>The two channels carry genuinely different secrets — six digits a person retypes, versus a
 * long random token they click — and the shape of each is defined here rather than being decided ad
 * hoc at each call site.
 */
public enum VerificationChannel {

    /** A six digit code, short lived because it is short. */
    PHONE(6, java.time.Duration.ofMinutes(5)),

    /**
     * A long random token delivered as a link. It survives far longer than an OTP because it has
     * enough entropy that time is not what protects it.
     */
    EMAIL(43, java.time.Duration.ofHours(24));

    private final int secretLength;
    private final java.time.Duration defaultTtl;

    VerificationChannel(int secretLength, java.time.Duration defaultTtl) {
        this.secretLength = secretLength;
        this.defaultTtl = defaultTtl;
    }

    /** Digits for a phone code; base64url characters for an email token. */
    public int secretLength() {
        return secretLength;
    }

    public java.time.Duration defaultTtl() {
        return defaultTtl;
    }

    public boolean isNumericCode() {
        return this == PHONE;
    }
}
