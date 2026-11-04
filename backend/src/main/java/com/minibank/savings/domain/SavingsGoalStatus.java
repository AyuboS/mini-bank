package com.minibank.savings.domain;

/** Where a goal is in its life. */
public enum SavingsGoalStatus {
    /** Being saved into. */
    ACTIVE,
    /** The target has been reached. Money can still be added or taken out. */
    ACHIEVED,
    /** Put away by the customer. Hidden from the main list; the money is untouched. */
    ARCHIVED
}
