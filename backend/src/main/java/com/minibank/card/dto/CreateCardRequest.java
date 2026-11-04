package com.minibank.card.dto;

import com.minibank.card.domain.CardDesign;
import com.minibank.card.domain.CardNetwork;
import com.minibank.card.domain.CardType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Ordering a card.
 *
 * <p>The account is optional: with one account there is nothing to choose, and the service falls
 * back to the customer's first one rather than making the client ask.
 */
@Schema(name = "CreateCardRequest")
public record CreateCardRequest(
        @Schema(description = "Account to attach the card to; defaults to your first account") UUID accountId,
        @NotNull CardType type,
        @NotNull CardDesign design,
        @Schema(defaultValue = "VISA") CardNetwork network,
        @Size(max = 50) @Schema(example = "Everyday") String label) {}
