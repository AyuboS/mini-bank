package com.minibank.account.dto;

import com.minibank.common.money.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Confirmation of payee: who an account number actually belongs to.
 *
 * <h2>What this deliberately does not contain</h2>
 *
 * <p>A first name and a last initial — "Jane S." — and the currency. Not the
 * full name, not the email, not the account's balance or status. Enough for the
 * payer to notice they have typed the wrong digit, and nothing more.
 *
 * <p>This endpoint is an account-existence oracle by its nature, and that is
 * accepted rather than overlooked: the alternative is customers sending money
 * into the void on a typo, which is the single most common way people lose money
 * in a banking app. The exposure is bounded by requiring authentication, by rate
 * limiting, and by returning only what a payer needs to check.
 */
@Schema(name = "PayeeLookupResponse")
public record PayeeLookupResponse(
        @Schema(example = "MB-482910375512") String accountNumber,
        @Schema(description = "First name and last initial only", example = "Jane S.") String holderName,
        CurrencyCode currency,
        @Schema(description = "Always MBank: this bank has no external network attached") String bank) {}
