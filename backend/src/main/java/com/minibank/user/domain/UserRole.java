package com.minibank.user.domain;

/** Coarse grained role used for authorization. */
public enum UserRole {
    /** An ordinary account holder. May only ever touch their own resources. */
    CUSTOMER,
    /** Back-office operator. May inspect any customer and perform administrative actions. */
    ADMIN;

    /** Spring Security expects authorities to carry the {@code ROLE_} prefix. */
    public String authority() {
        return "ROLE_" + name();
    }
}
