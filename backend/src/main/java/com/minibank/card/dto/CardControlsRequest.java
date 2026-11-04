package com.minibank.card.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Which channels a card may be used through.
 *
 * <p>Every field is a {@link Boolean} rather than a {@code boolean}: null means "leave this one
 * alone". With primitives, a client that wanted to turn off only the magnetic stripe would silently
 * send {@code false} for the other four and switch the whole card off, which is precisely the kind
 * of bug an API should make unrepresentable rather than document.
 */
@Schema(name = "CardControlsRequest")
public record CardControlsRequest(
        @Schema(description = "Card-not-present payments") Boolean onlinePayments,
        @Schema(description = "Tap to pay") Boolean contactless,
        @Schema(description = "Cash withdrawals at an ATM") Boolean atmWithdrawals,
        @Schema(description = "Use outside the card's home country") Boolean international,
        @Schema(description = "Fallback swipe. Off by default; it has no cryptographic protection.")
                Boolean magneticStripe) {}
