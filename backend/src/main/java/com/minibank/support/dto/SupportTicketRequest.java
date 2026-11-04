package com.minibank.support.dto;

import com.minibank.support.domain.SupportCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Open a support ticket. */
@Schema(name = "SupportTicketRequest")
public record SupportTicketRequest(
        @NotBlank @Size(max = 140) @Schema(example = "My card was declined at a shop") String subject,
        @NotNull @Schema(example = "CARD") SupportCategory category,
        @NotBlank
                @Size(max = 4000)
                @Schema(description = "What happened. Never include your password or a card's security code.")
                String description) {}
