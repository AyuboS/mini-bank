package com.minibank.transfer.dto;

import com.minibank.common.money.CurrencyCode;
import com.minibank.transfer.domain.TransactionCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A request to move money between two MBank accounts.
 *
 * <p>The source is identified by id (the caller owns it, so they know it) and the destination by
 * account number (that is what a payee shares). {@code currency} is required and must match the
 * source account: making the client state it turns a mistake into a rejected request rather than a
 * payment in the wrong currency.
 *
 * <p>{@code @Digits} bounds the value before it ever reaches the domain, so an absurd amount is
 * rejected by validation with a field-level message instead of by an arithmetic overflow.
 */
@Schema(name = "TransferRequest")
public record TransferRequest(
        @NotNull @Schema(description = "Id of one of your own accounts") UUID fromAccountId,
        @NotNull
                @Pattern(regexp = "^MB-[0-9]{12}$", message = "must be a valid MBank account number")
                @Schema(example = "MB-482910375512")
                String toAccountNumber,
        @NotNull
                @Positive(message = "must be greater than zero")
                @Digits(integer = 15, fraction = 2, message = "supports at most 2 decimal places")
                @Schema(example = "100.50")
                BigDecimal amount,
        @NotNull @Schema(example = "EUR") CurrencyCode currency,
        @Size(max = 140) @Schema(example = "Rent") String description,
        @Schema(
                        description =
                                "Optional. Omit it and the server derives one from the description; "
                                        + "a value here is always kept as given.",
                        example = "BILLS")
                TransactionCategory category) {}
