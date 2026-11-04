package com.minibank.card.dto;

import com.minibank.card.domain.Card;
import com.minibank.card.domain.CardDesign;
import com.minibank.card.domain.CardNetwork;
import com.minibank.card.domain.CardStatus;
import com.minibank.card.domain.CardType;
import com.minibank.common.money.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A card as the wallet sees it.
 *
 * <p>Carries the last four digits and nothing more of the number. The full number and the
 * verification value are reachable only through the separate reveal endpoint, so an ordinary list
 * response can be cached, logged or screenshotted without leaking a card.
 */
@Schema(name = "CardResponse")
public record CardResponse(
        UUID id,
        UUID accountId,
        @Schema(example = "MB-4821-9930") String accountNumber,
        CurrencyCode currency,
        @Schema(example = "Everyday") String label,
        CardType type,
        CardStatus status,
        CardDesign design,
        CardNetwork network,
        @Schema(example = "4284 •••• •••• 7351") String maskedNumber,
        @Schema(example = "7351") String lastFour,
        @Schema(example = "AYUBKHON SALIMOV") String cardholderName,
        @Schema(example = "8") int expirationMonth,
        @Schema(example = "2029") int expirationYear,
        BigDecimal dailyLimit,
        BigDecimal monthlyLimit,
        @Schema(description = "Channel switches and the caps that go with them")
                CardControlsResponse controls,
        Instant frozenAt,
        Instant createdAt) {

    public static CardResponse from(Card card, Instant now) {
        return new CardResponse(
                card.getId(),
                card.getAccount().getId(),
                card.getAccount().getAccountNumber(),
                card.getAccount().getCurrency(),
                card.getLabel(),
                card.getCardType(),
                card.effectiveStatus(now),
                card.getDesign(),
                card.getNetwork(),
                card.maskedNumber(),
                card.getLastFour(),
                card.getCardholderName(),
                card.getExpirationMonth(),
                card.getExpirationYear(),
                card.getDailyLimit(),
                card.getMonthlyLimit(),
                CardControlsResponse.from(card),
                card.getFrozenAt(),
                card.getCreatedAt());
    }
}
