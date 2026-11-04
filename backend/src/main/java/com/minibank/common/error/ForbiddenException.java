package com.minibank.common.error;

import java.io.Serial;

/** Raised when an authenticated caller lacks permission for the requested operation. */
public class ForbiddenException extends ApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ForbiddenException() {
        super(ErrorCode.FORBIDDEN);
    }

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
