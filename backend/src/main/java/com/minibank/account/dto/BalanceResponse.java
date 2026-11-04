package com.minibank.account.dto;

import com.minibank.account.domain.BankAccount;
import com.minibank.common.money.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The balance of a single account at a point in time. */
@Schema(name = "BalanceResponse")
public record BalanceResponse(
        UUID accountId,
        String accountNumber,
        CurrencyCode currency,
        @Schema(example = "4250.5000") BigDecimal balance,
        Instant asOf) {

    public static BalanceResponse from(BankAccount account, Instant asOf) {
        return new BalanceResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getCurrency(),
                account.getBalance(),
                asOf);
    }
}
