package com.minibank.common.error;

import java.io.Serial;

/**
 * Raised when a request is well formed and authorized but violates a domain rule, such as
 * transferring more money than an account holds.
 */
public class BusinessRuleException extends ApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BusinessRuleException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BusinessRuleException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
