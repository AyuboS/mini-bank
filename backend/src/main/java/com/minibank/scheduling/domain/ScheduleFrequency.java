package com.minibank.scheduling.domain;

import java.time.LocalDate;

/**
 * How often a standing order repeats.
 *
 * <p>The date arithmetic lives here rather than in the runner so that "the 31st of every month"
 * has exactly one answer in the codebase.
 */
public enum ScheduleFrequency {
    /** Runs once, then completes. */
    ONCE,
    WEEKLY,
    MONTHLY;

    /**
     * The next date after {@code from}, or null when there is no next run.
     *
     * <p>{@link java.time.LocalDate#plusMonths} clamps to the end of a short month, so an order set
     * up on the 31st runs on the 30th of April and the 28th of February. Clamping is the
     * conventional banking behaviour and the only one that never skips a month.
     */
    public LocalDate next(LocalDate from) {
        return switch (this) {
            case ONCE -> null;
            case WEEKLY -> from.plusWeeks(1);
            case MONTHLY -> from.plusMonths(1);
        };
    }

    public boolean repeats() {
        return this != ONCE;
    }
}
