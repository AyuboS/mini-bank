package com.minibank.common.error;

import java.io.Serial;

/** Raised when a requested resource does not exist, or is not visible to the caller. */
public class NotFoundException extends ApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
