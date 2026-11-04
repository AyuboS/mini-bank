package com.minibank.common.validation;

/**
 * The single definition of what counts as an acceptable password.
 *
 * <p>The upper bound is not arbitrary: BCrypt silently truncates input beyond 72 bytes, so
 * accepting a longer password would give the user a false sense of strength.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 72;

    private PasswordPolicy() {}

    /** True when the candidate satisfies every rule of the policy. */
    public static boolean isValid(String password) {
        if (password == null) {
            return false;
        }
        int length = password.length();
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            return false;
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (int i = 0; i < length; i++) {
            char c = password.charAt(i);
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                hasSpecial = true;
            }
        }
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    public static String describe() {
        return "Password must be %d-%d characters and contain an upper case letter, a lower case letter, "
                .formatted(MIN_LENGTH, MAX_LENGTH) + "a digit and a special character";
    }
}
