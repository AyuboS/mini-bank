package com.minibank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.money.CurrencyCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BankAccountTest {

    private static BankAccount customerAccount(String balance) {
        BankAccount account = BankAccount.openCustomerAccount(null, "MB-000000000001", CurrencyCode.EUR);
        account.setBalance(new BigDecimal(balance));
        return account;
    }

    @Test
    void debitReducesTheBalance() {
        BankAccount account = customerAccount("100.0000");

        account.debit(new BigDecimal("40.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("60.00");
    }

    @Test
    void creditIncreasesTheBalance() {
        BankAccount account = customerAccount("100.0000");

        account.credit(new BigDecimal("40.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("140.00");
    }

    @Test
    void debitOfTheEntireBalanceIsAllowed() {
        BankAccount account = customerAccount("100.0000");

        account.debit(new BigDecimal("100.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void aCustomerAccountCannotBeOverdrawn() {
        BankAccount account = customerAccount("100.0000");

        assertThatThrownBy(() -> account.debit(new BigDecimal("100.01")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);

        assertThat(account.getBalance()).as("balance untouched by the failed debit").isEqualByComparingTo("100");
    }

    @Test
    void aSystemAccountMayGoNegative() {
        // A negative system balance is the bank's liability to its customers, not an error.
        BankAccount system = BankAccount.openCustomerAccount(null, "MB-SYSTEM-EUR", CurrencyCode.EUR);
        system.setAccountType(AccountType.SYSTEM);

        system.debit(new BigDecimal("500.00"));

        assertThat(system.getBalance()).isEqualByComparingTo("-500.00");
    }

    @Test
    void balancesKeepTheStorageScaleAcrossOperations() {
        BankAccount account = customerAccount("100.0000");

        account.debit(new BigDecimal("0.1"));
        account.credit(new BigDecimal("0.1"));

        assertThat(account.getBalance()).isEqualByComparingTo("100").hasToString("100.0000");
    }

    @Test
    void anActiveAccountMayTransact() {
        assertThatCode(() -> customerAccount("1").requireTransactable()).doesNotThrowAnyException();
    }

    @Test
    void aFrozenAccountMayNotTransact() {
        BankAccount account = customerAccount("100");
        account.setStatus(AccountStatus.FROZEN);

        assertThatThrownBy(account::requireTransactable)
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_FROZEN);
    }

    @Test
    void aClosedAccountMayNotTransact() {
        BankAccount account = customerAccount("100");
        account.setStatus(AccountStatus.CLOSED);

        assertThatThrownBy(account::requireTransactable)
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_CLOSED);
    }
}
