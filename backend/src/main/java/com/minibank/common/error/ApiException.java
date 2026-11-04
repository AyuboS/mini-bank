package com.minibank.common.error;

import java.io.Serial;

/**
 * Base class for every error the application deliberately raises.
 *
 * <p>Carrying an {@link ErrorCode} means the exception already knows both its HTTP status and its
 * machine-readable code, so the global handler never has to guess.
 */
public class ApiException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
