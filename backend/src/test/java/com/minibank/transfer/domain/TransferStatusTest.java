package com.minibank.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TransferStatusTest {

    @Test
    void pendingMayOnlyProceedOrFail() {
        assertThat(allowedFrom(TransferStatus.PENDING))
                .containsExactlyInAnyOrder(TransferStatus.PROCESSING, TransferStatus.FAILED);
    }

    @Test
    void processingMayOnlySettleOrFail() {
        assertThat(allowedFrom(TransferStatus.PROCESSING))
                .containsExactlyInAnyOrder(TransferStatus.COMPLETED, TransferStatus.FAILED);
    }

    @Test
    void aSettledTransferMayOnlyBeReversed() {
        assertThat(allowedFrom(TransferStatus.COMPLETED)).containsExactly(TransferStatus.REVERSED);
    }

    @ParameterizedTest
    @EnumSource(value = TransferStatus.class, names = {"FAILED", "REVERSED"})
    void terminalStatusesAllowNoFurtherTransition(TransferStatus terminal) {
        assertThat(allowedFrom(terminal)).isEmpty();
        assertThat(terminal.isTerminal()).isTrue();
    }

    @Test
    void aCompletedTransferCannotBeWalkedBackToPending() {
        // The specific regression this state machine exists to prevent.
        assertThat(TransferStatus.COMPLETED.canTransitionTo(TransferStatus.PENDING)).isFalse();
        assertThat(TransferStatus.COMPLETED.canTransitionTo(TransferStatus.PROCESSING)).isFalse();
        assertThat(TransferStatus.COMPLETED.canTransitionTo(TransferStatus.COMPLETED)).isFalse();
    }

    @Test
    void aFailedTransferCannotBecomeCompleted() {
        assertThat(TransferStatus.FAILED.canTransitionTo(TransferStatus.COMPLETED)).isFalse();
    }

    @Test
    void settledStatusesAreCompletedAndReversed() {
        assertThat(EnumSet.allOf(TransferStatus.class).stream()
                        .filter(TransferStatus::isSettled)
                        .toList())
                .containsExactlyInAnyOrder(TransferStatus.COMPLETED, TransferStatus.REVERSED);
    }

    private static Set<TransferStatus> allowedFrom(TransferStatus from) {
        return EnumSet.allOf(TransferStatus.class).stream()
                .filter(from::canTransitionTo)
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(TransferStatus.class)));
    }
}
