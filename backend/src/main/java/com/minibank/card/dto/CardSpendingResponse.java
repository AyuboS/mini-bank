package com.minibank.card.dto;

import com.minibank.common.money.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * How much of a card's caps has been used.
 *
 * <p>Measured over the account the card is attached to. MBank has no card acquiring, so there is no
 * separate stream of card authorisations to draw on; spending on the account is the honest
 * available proxy, and the field name says so rather than implying a precision that does not exist.
 */
@Schema(name = "CardSpendingResponse")
public record CardSpendingResponse(
        UUID cardId,
        CurrencyCode currency,
        @Schema(description = "Outgoing settled amount on the account today, UTC") BigDecimal spentToday,
        @Schema(description = "Outgoing settled amount on the account this calendar month")
                BigDecimal spentThisMonth,
        BigDecimal dailyLimit,
        BigDecimal monthlyLimit) {}
