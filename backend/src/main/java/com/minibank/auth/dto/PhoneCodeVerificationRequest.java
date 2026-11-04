package com.minibank.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Presents a code that was sent by SMS. */
@Schema(name = "PhoneCodeVerificationRequest")
public record PhoneCodeVerificationRequest(
        @NotBlank @Size(max = 30) @Schema(example = "+998 90 123 45 67") String phoneNumber,
        @NotBlank
                @Pattern(regexp = "^[0-9]{4,8}$", message = "must be the digits from the message")
                @Schema(example = "482913")
                String code) {

    /** Keeps the code out of any accidental {@code toString()} in a log or a stack trace. */
    @Override
    public String toString() {
        return "PhoneCodeVerificationRequest[phoneNumber=***, code=***]";
    }
}
