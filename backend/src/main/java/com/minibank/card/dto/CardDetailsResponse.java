package com.minibank.card.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * The sensitive view of a card, returned only by an explicit reveal.
 *
 * <p>Every field here is either decrypted or derived at the moment of the call; none of it is
 * cached, and the response carries {@code Cache-Control: no-store}. {@code simulated} is part of
 * the contract rather than a footnote: MBank issues no real credentials, and a client that renders
 * these values is expected to say so on screen.
 */
@Schema(name = "CardDetailsResponse")
public record CardDetailsResponse(
        UUID cardId,
        @Schema(example = "AYUBKHON SALIMOV") String cardholderName,
        @Schema(example = "4284193948217351") String number,
        @Schema(example = "8") int expirationMonth,
        @Schema(example = "2029") int expirationYear,
        @Schema(example = "417") String verificationValue,
        @Schema(description = "Always true: these are demo credentials, valid nowhere") boolean simulated,
        Instant revealedAt) {}
