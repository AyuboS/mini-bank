package com.minibank.beneficiary.domain;

/** Lifecycle of a saved payee. */
public enum BeneficiaryStatus {
    ACTIVE,
    /** Soft deleted. The row is kept so that risk rules can still see the relationship history. */
    DELETED
}
