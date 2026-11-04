package com.minibank.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minibank.account.repository.BankAccountRepository;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountNumberGeneratorTest {

    @Mock
    private BankAccountRepository bankAccountRepository;

    @InjectMocks
    private AccountNumberGenerator generator;

    @Test
    void producesTheDocumentedFormat() {
        when(bankAccountRepository.existsByAccountNumber(anyString())).thenReturn(false);

        assertThat(generator.generate()).matches("^MB-[0-9]{12}$");
    }

    @Test
    void producesUnpredictableNumbersRatherThanASequence() {
        when(bankAccountRepository.existsByAccountNumber(anyString())).thenReturn(false);

        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            generated.add(generator.generate());
        }

        assertThat(generated).as("no collisions across 500 draws").hasSize(500);
    }

    @Test
    void retriesWhenTheDatabaseAlreadyHoldsTheNumber() {
        when(bankAccountRepository.existsByAccountNumber(anyString()))
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(false);

        assertThat(generator.generate()).matches("^MB-[0-9]{12}$");
        verify(bankAccountRepository, times(3)).existsByAccountNumber(anyString());
    }

    @Test
    void givesUpRatherThanLoopingForeverWhenEveryCandidateCollides() {
        when(bankAccountRepository.existsByAccountNumber(anyString())).thenReturn(true);

        assertThatThrownBy(generator::generate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unique account number");
    }
}
