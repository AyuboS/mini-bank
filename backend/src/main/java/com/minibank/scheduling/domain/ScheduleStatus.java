package com.minibank.scheduling.domain;

/** Where a standing order is in its life. */
public enum ScheduleStatus {
    /** Live. The runner will pick it up when it falls due. */
    SCHEDULED,
    /** Held by the customer. Due dates still advance; nothing is sent. */
    PAUSED,
    /** Ran its course: a one-off that executed, or a repeating order past its end date. */
    COMPLETED,
    /** Stopped by the customer. Terminal. */
    CANCELLED;

    public boolean isLive() {
        return this == SCHEDULED || this == PAUSED;
    }
}
