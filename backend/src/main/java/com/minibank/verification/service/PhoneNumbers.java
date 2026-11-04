package com.minibank.verification.service;

import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
import java.util.regex.Pattern;

/**
 * Normalisation of phone numbers to E.164.
 *
 * <p>The whole point is that {@code +998 90 123 45 67}, {@code +998901234567} and {@code
 * 00998901234567} are one number, not three. Storing them as typed would mean a customer could
 * register twice, fail to log in with the number they gave, and receive an OTP at a destination the
 * lookup cannot match — all from formatting.
 *
 * <p>Deliberately not a full libphonenumber-style validation. That would need the carrier and
 * area-code tables for every country and a dependency to carry them; what matters here is that one
 * number always produces one string, and that obvious rubbish is rejected.
 */
public final class PhoneNumbers {

    /** E.164 allows a leading digit 1-9 followed by up to 14 more. */
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private static final Pattern SEPARATORS = Pattern.compile("[\\s().\\-–—/]");

    private PhoneNumbers() {}

    /**
     * Returns the number in E.164 form.
     *
     * @throws BusinessRuleException with {@link ErrorCode#PHONE_NUMBER_INVALID} if it cannot be one
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessRuleException(ErrorCode.PHONE_NUMBER_INVALID, "A phone number is required");
        }

        String cleaned = SEPARATORS.matcher(raw.trim()).replaceAll("");

        // 00 is the other way of writing the international prefix, common across Europe and the CIS.
        if (cleaned.startsWith("00")) {
            cleaned = "+" + cleaned.substring(2);
        } else if (!cleaned.startsWith("+")) {
            // Bare digits are taken to already include the country code. A number short enough to be
            // a local subscriber number is rejected rather than guessed at, because guessing the
            // country wrong sends somebody's login code to a stranger.
            cleaned = "+" + cleaned;
        }

        if (!E164.matcher(cleaned).matches()) {
            throw new BusinessRuleException(
                    ErrorCode.PHONE_NUMBER_INVALID,
                    "Enter the number in international format, for example +998901234567");
        }
        return cleaned;
    }

    /** Normalises without throwing, for callers that treat an unusable number as simply absent. */
    public static java.util.Optional<String> tryNormalize(String raw) {
        try {
            return java.util.Optional.of(normalize(raw));
        } catch (BusinessRuleException ignored) {
            return java.util.Optional.empty();
        }
    }

    /**
     * The number as it is safe to show or log: country code, then the last two digits.
     *
     * <p>Enough for somebody to recognise their own number on a "we sent a code to..." screen, and
     * not enough for anyone else to learn it.
     */
    public static String mask(String e164) {
        if (e164 == null || e164.length() < 6) {
            return "•••";
        }
        String countryPart = e164.substring(0, Math.min(4, e164.length() - 2));
        return countryPart + " ••• •• " + e164.substring(e164.length() - 2);
    }
}
