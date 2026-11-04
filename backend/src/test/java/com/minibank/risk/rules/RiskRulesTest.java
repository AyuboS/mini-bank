package com.minibank.risk.rules;

import static com.minibank.risk.RiskTestFixtures.account;
import static com.minibank.risk.RiskTestFixtures.context;
import static com.minibank.risk.RiskTestFixtures.defaultProperties;
import static com.minibank.risk.RiskTestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.domain.AuditOutcome;
import com.minibank.audit.repository.AuditLogRepository;
import com.minibank.beneficiary.repository.BeneficiaryRepository;
import com.minibank.risk.domain.RiskSignal;
import com.minibank.transfer.repository.TransferRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiskRulesTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private BeneficiaryRepository beneficiaryRepository;

    @Nested
    @DisplayName("Rule 1: large amount")
    class LargeAmount {

        private final LargeAmountRule rule = new LargeAmountRule(defaultProperties());

        @Test
        void staysSilentBelowTheThreshold() {
            assertThat(rule.evaluate(context("2499.99"))).isEmpty();
        }

        @Test
        void firesAtTheThreshold() {
            Optional<RiskSignal> signal = rule.evaluate(context("2500.00"));

            assertThat(signal).isPresent();
            assertThat(signal.get().score()).isEqualTo(35);
            assertThat(signal.get().blocking()).isFalse();
        }

        @Test
        void explainsItselfInTermsAnOperatorCanRead() {
            assertThat(rule.evaluate(context("3000.00")).orElseThrow().reason())
                    .contains("3000")
                    .contains("EUR")
                    .contains("2500");
        }
    }

    @Nested
    @DisplayName("Rule 2: velocity")
    class Velocity {

        @Test
        void staysSilentBelowTheBurstThreshold() {
            var rule = new TransferVelocityRule(defaultProperties(), transferRepository);
            when(transferRepository.countByInitiatedByIdAndCreatedAtAfter(any(), any())).thenReturn(4L);

            assertThat(rule.evaluate(context("10"))).isEmpty();
        }

        @Test
        void firesOnABurst() {
            var rule = new TransferVelocityRule(defaultProperties(), transferRepository);
            when(transferRepository.countByInitiatedByIdAndCreatedAtAfter(any(), any())).thenReturn(7L);

            var signal = rule.evaluate(context("10"));

            assertThat(signal).isPresent();
            assertThat(signal.get().score()).isEqualTo(30);
            assertThat(signal.get().reason()).contains("7 transfers");
        }
    }

    @Nested
    @DisplayName("Rule 3: unusual amount for this customer")
    class UnusualAmount {

        @Test
        void staysSilentWhenTheCustomerHasNoHistoryToCompareAgainst() {
            var rule = new UnusualAmountRule(defaultProperties(), transferRepository);
            when(transferRepository.averageCompletedAmountSince(any(), any())).thenReturn(null);

            assertThat(rule.evaluate(context("1000"))).isEmpty();
        }

        @Test
        void staysSilentBelowTheAbsoluteFloor() {
            // Somebody whose history is a few one-euro payments is not flagged for spending twenty.
            var rule = new UnusualAmountRule(defaultProperties(), transferRepository);

            assertThat(rule.evaluate(context("20"))).isEmpty();
        }

        @Test
        void staysSilentWithinTheCustomersNormalRange() {
            var rule = new UnusualAmountRule(defaultProperties(), transferRepository);
            when(transferRepository.averageCompletedAmountSince(any(), any())).thenReturn(new BigDecimal("200"));

            assertThat(rule.evaluate(context("900"))).isEmpty();
        }

        @Test
        void firesWellAboveTheCustomersNormalRange() {
            var rule = new UnusualAmountRule(defaultProperties(), transferRepository);
            when(transferRepository.averageCompletedAmountSince(any(), any())).thenReturn(new BigDecimal("100"));

            var signal = rule.evaluate(context("1000"));

            assertThat(signal).isPresent();
            assertThat(signal.get().score()).isEqualTo(25);
            assertThat(signal.get().reason()).contains("average");
        }
    }

    @Nested
    @DisplayName("Rule 4: recent failed logins")
    class FailedLogins {

        @Test
        void staysSilentBelowTheThreshold() {
            var rule = new RecentFailedLoginsRule(defaultProperties(), auditLogRepository);
            when(auditLogRepository.countByUserIdAndActionAndOutcomeAndCreatedAtAfter(
                            any(UUID.class), eq(AuditAction.LOGIN_FAILED), eq(AuditOutcome.FAILURE), any(Instant.class)))
                    .thenReturn(2L);

            assertThat(rule.evaluate(context("10"))).isEmpty();
        }

        @Test
        void firesWhenAuthenticationWasRepeatedlyRefusedJustBefore() {
            var rule = new RecentFailedLoginsRule(defaultProperties(), auditLogRepository);
            when(auditLogRepository.countByUserIdAndActionAndOutcomeAndCreatedAtAfter(
                            any(UUID.class), eq(AuditAction.LOGIN_FAILED), eq(AuditOutcome.FAILURE), any(Instant.class)))
                    .thenReturn(4L);

            var signal = rule.evaluate(context("10"));

            assertThat(signal).isPresent();
            assertThat(signal.get().score()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("Rule 5: large payment to a brand new payee")
    class NewBeneficiary {

        @Test
        void staysSilentForASmallPaymentToANewPayee() {
            var rule = new NewBeneficiaryRule(defaultProperties(), beneficiaryRepository);

            assertThat(rule.evaluate(context("50"))).isEmpty();
        }

        @Test
        void staysSilentForALargePaymentToAnEstablishedPayee() {
            var rule = new NewBeneficiaryRule(defaultProperties(), beneficiaryRepository);
            when(beneficiaryRepository.existsRecentlyAddedForOwner(any(), anyString(), any()))
                    .thenReturn(false);

            assertThat(rule.evaluate(context("2000"))).isEmpty();
        }

        @Test
        void firesOnTheCombinationOfBoth() {
            var rule = new NewBeneficiaryRule(defaultProperties(), beneficiaryRepository);
            when(beneficiaryRepository.existsRecentlyAddedForOwner(any(), anyString(), any()))
                    .thenReturn(true);

            var signal = rule.evaluate(context("2000"));

            assertThat(signal).isPresent();
            assertThat(signal.get().score()).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("Rule 6: balance sweep")
    class BalanceSweep {

        private final BalanceSweepRule rule = new BalanceSweepRule();

        @Test
        void staysSilentForAnOrdinaryFractionOfTheBalance() {
            var ctx = context(user(), account("MB-000000000001", "1000"), account("MB-000000000002", "0"), "500");

            assertThat(rule.evaluate(ctx)).isEmpty();
        }

        @Test
        void firesWhenNearlyTheWholeBalanceLeavesAtOnce() {
            var ctx = context(user(), account("MB-000000000001", "1000"), account("MB-000000000002", "0"), "950");

            var signal = rule.evaluate(ctx);

            assertThat(signal).isPresent();
            assertThat(signal.get().reason()).contains("95%");
        }

        @Test
        void staysSilentOnAnEmptyAccountRatherThanDividingByZero() {
            var ctx = context(user(), account("MB-000000000001", "0"), account("MB-000000000002", "0"), "50");

            assertThat(rule.evaluate(ctx)).isEmpty();
        }
    }
}
