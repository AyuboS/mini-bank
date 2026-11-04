package com.minibank.card.dto;

import com.minibank.card.domain.Card;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** The full control surface of one card: five switches and the four caps that go with them. */
@Schema(name = "CardControlsResponse")
public record CardControlsResponse(
        boolean onlinePayments,
        boolean contactless,
        boolean atmWithdrawals,
        boolean international,
        boolean magneticStripe,
        @Schema(description = "Null means no card-level cap; the account's transfer limits still apply")
                BigDecimal dailyLimit,
        BigDecimal monthlyLimit,
        BigDecimal atmLimit,
        BigDecimal onlineLimit) {

    public static CardControlsResponse from(Card card) {
        return new CardControlsResponse(
                card.isOnlinePaymentsEnabled(),
                card.isContactlessEnabled(),
                card.isAtmWithdrawalsEnabled(),
                card.isInternationalEnabled(),
                card.isMagneticStripeEnabled(),
                card.getDailyLimit(),
                card.getMonthlyLimit(),
                card.getAtmLimit(),
                card.getOnlineLimit());
    }
}
