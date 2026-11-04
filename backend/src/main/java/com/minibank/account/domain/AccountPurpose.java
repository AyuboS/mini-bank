package com.minibank.account.domain;

/**
 * What an account is for, from the customer's point of view.
 *
 * <p>Distinct from {@link AccountType}, which says who owns the account and whether it may go
 * negative. Purpose is a product classification and carries no banking rules: a savings account
 * settles, freezes and is limited exactly like a spending one. It exists so the wallet can group
 * accounts sensibly and so money put aside for a goal is not presented as money available to spend.
 */
public enum AccountPurpose {
    /** Day-to-day money. What every account was before savings goals existed. */
    SPENDING,
    /** Money set aside, backing a savings goal. */
    SAVINGS
}
