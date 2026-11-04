package com.minibank.account.dto;

import com.minibank.account.domain.AccountPurpose;
import com.minibank.account.domain.AccountStatus;
import com.minibank.account.domain.BankAccount;
import com.minibank.common.money.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A bank account as returned to its owner. */
@Schema(name = "AccountResponse")
public record AccountResponse(
        UUID id,
        @Schema(example = "MB-482910375512") String accountNumber,
        CurrencyCode currency,
        @Schema(description = "SPENDING for day-to-day money, SAVINGS when the account backs a goal")
                AccountPurpose purpose,
        AccountStatus status,
        @Schema(example = "4250.5000") BigDecimal balance,
        Instant createdAt) {

    public static AccountResponse from(BankAccount account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getCurrency(),
                account.getPurpose(),
                account.getStatus(),
                account.getBalance(),
                account.getCreatedAt());
    }
}
