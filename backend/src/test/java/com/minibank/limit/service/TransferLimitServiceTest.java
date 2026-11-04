package com.minibank.limit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.minibank.audit.service.AuditService;
import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.money.CurrencyCode;
import com.minibank.limit.config.TransferLimitProperties;
import com.minibank.limit.domain.TransferLimitOverride;
import com.minibank.limit.repository.TransferLimitOverrideRepository;
import com.minibank.transfer.repository.TransferRepository;
import com.minibank.user.domain.User;
import com.minibank.user.domain.UserRole;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferLimitServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");

    @Mock
    private TransferLimitOverrideRepository overrideRepository;

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private AuditService auditService;

    private TransferLimitService service;
    private User user;

    @BeforeEach
    void setUp() {
        var defaults = new TransferLimitProperties(new BigDecimal("5000.00"), new BigDecimal("10000.00"), 20);
        service = new TransferLimitService(
                defaults, overrideRepository, transferRepository, auditService, Clock.fixed(NOW, ZoneOffset.UTC));
        user = User.create("john", "john@example.com", "hash", "John", "Smith", UserRole.CUSTOMER);
        user.setId(UUID.randomUUID());
    }

    private void noOverride() {
        when(overrideRepository.findByUserId(any())).thenReturn(Optional.empty());
    }

    private void alreadySentToday(String amount) {
        lenient()
                .when(transferRepository.sumAmountByInitiatorSince(any(), any(), anyCollection()))
                .thenReturn(new BigDecimal(amount));
    }

    private void transfersInTheLastHour(long count) {
        lenient()
                .when(transferRepository.countByInitiatedByIdAndCreatedAtAfter(any(), any()))
                .thenReturn(count);
    }

    @Test
    void allowsATransferWithinEveryLimit() {
        noOverride();
        alreadySentToday("0");
        transfersInTheLastHour(0);

        assertThatCode(() -> service.checkWithinLimits(user, new BigDecimal("100.00"), CurrencyCode.EUR))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsASingleTransferAboveThePerTransferCeiling() {
        noOverride();

        assertThatThrownBy(() -> service.checkWithinLimits(user, new BigDecimal("5000.01"), CurrencyCode.EUR))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TRANSFER_LIMIT_EXCEEDED);
    }

    @Test
    void allowsATransferExactlyAtThePerTransferCeiling() {
        noOverride();
        alreadySentToday("0");
        transfersInTheLastHour(0);

        assertThatCode(() -> service.checkWithinLimits(user, new BigDecimal("5000.00"), CurrencyCode.EUR))
                .doesNotThrowAnyException();
    }

    @Test
    void countsWhatWasAlreadySentTowardsTheDailyCeiling() {
        noOverride();
        alreadySentToday("9950.00");
        transfersInTheLastHour(1);

        assertThatThrownBy(() -> service.checkWithinLimits(user, new BigDecimal("100.00"), CurrencyCode.EUR))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("daily limit");
    }

    @Test
    void rejectsOnceTheHourlyTransferCountIsReached() {
        noOverride();
        alreadySentToday("0");
        transfersInTheLastHour(20);

        assertThatThrownBy(() -> service.checkWithinLimits(user, new BigDecimal("1.00"), CurrencyCode.EUR))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("per hour");
    }

    @Test
    void anOverrideRaisesTheCeilingForThatCustomerOnly() {
        TransferLimitOverride override = TransferLimitOverride.forUser(user);
        override.setPerTransferMax(new BigDecimal("50000.00"));
        when(overrideRepository.findByUserId(any())).thenReturn(Optional.of(override));
        alreadySentToday("0");
        transfersInTheLastHour(0);

        // 6000 is above the platform per-transfer default of 5000 but still inside the daily
        // ceiling, so it isolates the effect of the per-transfer override.
        assertThatCode(() -> service.checkWithinLimits(user, new BigDecimal("6000.00"), CurrencyCode.EUR))
                .doesNotThrowAnyException();
    }

    @Test
    void anOverriddenPerTransferCeilingDoesNotLiftTheDailyCeiling() {
        TransferLimitOverride override = TransferLimitOverride.forUser(user);
        override.setPerTransferMax(new BigDecimal("50000.00"));
        when(overrideRepository.findByUserId(any())).thenReturn(Optional.of(override));
        alreadySentToday("0");
        transfersInTheLastHour(0);

        assertThatThrownBy(() -> service.checkWithinLimits(user, new BigDecimal("20000.00"), CurrencyCode.EUR))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("daily limit");
    }

    @Test
    void anOverrideLeavesUnsetFieldsOnThePlatformDefault() {
        TransferLimitOverride override = TransferLimitOverride.forUser(user);
        override.setPerTransferMax(new BigDecimal("50000.00"));
        when(overrideRepository.findByUserId(any())).thenReturn(Optional.of(override));

        var limits = service.effectiveLimitsFor(user.getId());

        assertThat(limits.perTransferMax()).isEqualByComparingTo("50000.00");
        assertThat(limits.dailyMax()).as("falls back to the default").isEqualByComparingTo("10000.00");
        assertThat(limits.maxTransfersPerHour()).isEqualTo(20);
        assertThat(limits.customised()).isTrue();
    }

    @Test
    void reportsThePlatformDefaultsWhenNoOverrideExists() {
        noOverride();

        var limits = service.effectiveLimitsFor(user.getId());

        assertThat(limits.perTransferMax()).isEqualByComparingTo("5000.00");
        assertThat(limits.customised()).isFalse();
    }
}
