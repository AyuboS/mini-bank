package com.minibank.card.dto;

import com.minibank.card.domain.CardDesign;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * A partial update: rename a card, restyle it, or both.
 *
 * <p>Null means "leave it alone", which is what makes this a PATCH rather than a PUT.
 */
@Schema(name = "UpdateCardRequest")
public record UpdateCardRequest(@Size(min = 1, max = 50) String label, CardDesign design) {}
