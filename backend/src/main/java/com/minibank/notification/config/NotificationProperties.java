package com.minibank.notification.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Outbound message delivery, bound from {@code minibank.notifications.*}.
 *
 * @param appBaseUrl where a link in an email should point; the web client's origin
 * @param exposeCodes development aid: returns the OTP in the API response instead of requiring a
 *     real handset. Refused in the prod profile by {@code StartupSecurityCheck}.
 */
@Validated
@ConfigurationProperties(prefix = "minibank.notifications")
public record NotificationProperties(
        @NotBlank @DefaultValue("http://localhost:5173") String appBaseUrl,
        @DefaultValue("false") boolean exposeCodes,
        @Valid @NotNull Sms sms,
        @Valid @NotNull Email email) {

    /**
     * @param provider {@code dev} logs instead of sending; {@code twilio} sends for real
     * @param from the sender the recipient sees; a number or an alphanumeric sender id
     */
    public record Sms(
            @DefaultValue("dev") String provider,
            @DefaultValue("MBank") String from,
            @DefaultValue("") String accountSid,
            @DefaultValue("") String authToken) {}

    /**
     * @param provider {@code dev} logs instead of sending; {@code smtp} sends through JavaMail
     */
    public record Email(
            @DefaultValue("dev") String provider,
            @DefaultValue("MBank <no-reply@mbank.local>") String from) {}
}
