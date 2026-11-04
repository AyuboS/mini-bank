package com.minibank.account.dto;

import com.minibank.common.money.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Request to open an additional account for the authenticated customer. */
@Schema(name = "CreateAccountRequest")
public record CreateAccountRequest(@NotNull @Schema(example = "EUR") CurrencyCode currency) {}
