package com.minibank.risk.domain;

import com.minibank.account.domain.BankAccount;
import com.minibank.common.money.CurrencyCode;
import com.minibank.user.domain.User;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Everything a rule may look at.
 *
 * <p>Rules receive this record rather than reaching for services of their own, so the inputs to a
 * risk decision are explicit and a rule can be unit tested by constructing one value.
 */
public record RiskContext(
        User initiator,
        BankAccount sourceAccount,
        BankAccount destinationAccount,
        BigDecimal amount,
        CurrencyCode currency,
        Instant now) {}
