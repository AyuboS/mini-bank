package com.minibank.common.error;

import org.springframework.http.HttpStatus;

/**
 * Machine-readable error codes returned to API clients.
 *
 * <p>Clients are expected to branch on {@link #name()} rather than on the human readable message,
 * which may be reworded at any time. Each code carries the HTTP status it maps to so that the
 * mapping lives in exactly one place.
 */
public enum ErrorCode {

    // --- Authentication / authorization ---
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid username or password"),
    AUTH_ACCOUNT_LOCKED(HttpStatus.LOCKED, "Account is locked due to repeated failed logins"),
    AUTH_ACCOUNT_NOT_ACTIVE(HttpStatus.FORBIDDEN, "User account is not active"),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Authentication token is invalid"),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Authentication token has expired"),
    AUTH_REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Refresh token is invalid, expired or revoked"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You are not allowed to perform this operation"),

    // --- Users ---
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "Username is already taken"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email is already registered"),
    WEAK_PASSWORD(HttpStatus.BAD_REQUEST, "Password does not satisfy the password policy"),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "Current password is incorrect"),

    // --- Accounts ---
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Bank account not found"),
    ACCOUNT_FROZEN(HttpStatus.CONFLICT, "Bank account is frozen"),
    ACCOUNT_CLOSED(HttpStatus.CONFLICT, "Bank account is closed"),
    ACCOUNT_NOT_ACTIVE(HttpStatus.CONFLICT, "Bank account is not active"),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds on the source account"),

    // --- Transfers ---
    TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND, "Transfer not found"),
    TRANSFER_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Transfer limit exceeded"),
    CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "Source and destination currencies do not match"),
    SAME_ACCOUNT_TRANSFER(HttpStatus.UNPROCESSABLE_ENTITY, "Source and destination accounts must differ"),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "Transfer amount is invalid"),
    INVALID_TRANSFER_STATE(HttpStatus.CONFLICT, "Transfer is not in a state that allows this operation"),
    TRANSFER_ALREADY_REVERSED(HttpStatus.CONFLICT, "Transfer has already been reversed"),

    // --- Idempotency ---
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required for this operation"),
    IDEMPOTENCY_KEY_CONFLICT(
            HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key was already used with a different request payload"),
    IDEMPOTENCY_REQUEST_IN_PROGRESS(
            HttpStatus.CONFLICT, "A request with this idempotency key is currently being processed"),

    // --- Beneficiaries ---
    BENEFICIARY_NOT_FOUND(HttpStatus.NOT_FOUND, "Beneficiary not found"),
    BENEFICIARY_ALREADY_EXISTS(HttpStatus.CONFLICT, "Beneficiary already saved for this account number"),

    // --- Cards ---
    CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "Card not found"),
    CARD_NOT_ACTIVE(HttpStatus.CONFLICT, "Card is not in a state that allows this operation"),
    CARD_EXPIRED(HttpStatus.CONFLICT, "Card has expired"),
    CARD_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_ENTITY, "You already hold the maximum number of cards"),
    CARD_INVALID_LIMITS(HttpStatus.BAD_REQUEST, "The monthly limit must be at least the daily limit"),

    // --- Savings ---
    SAVINGS_GOAL_NOT_FOUND(HttpStatus.NOT_FOUND, "Savings goal not found"),
    SAVINGS_GOAL_ARCHIVED(HttpStatus.CONFLICT, "This savings goal is archived"),
    SAVINGS_GOAL_NOT_EMPTY(HttpStatus.CONFLICT, "Withdraw the money before closing this goal"),
    SAVINGS_GOAL_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_ENTITY, "You already have the maximum number of goals"),

    // --- Budgets ---
    BUDGET_NOT_FOUND(HttpStatus.NOT_FOUND, "Budget not found"),
    BUDGET_ALREADY_EXISTS(HttpStatus.CONFLICT, "A budget already exists for this category"),

    // --- Standing orders ---
    SCHEDULED_TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND, "Scheduled transfer not found"),
    SCHEDULED_TRANSFER_NOT_MODIFIABLE(
            HttpStatus.CONFLICT, "This scheduled transfer has finished and can no longer be changed"),
    SCHEDULED_DATE_IN_PAST(HttpStatus.BAD_REQUEST, "The first run must be today or later"),

    // --- Money requests ---
    MONEY_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "Payment request not found"),
    MONEY_REQUEST_NOT_PENDING(HttpStatus.CONFLICT, "This payment request has already been settled or withdrawn"),
    MONEY_REQUEST_EXPIRED(HttpStatus.GONE, "This payment request has expired"),

    // --- Support ---
    SUPPORT_TICKET_NOT_FOUND(HttpStatus.NOT_FOUND, "Support ticket not found"),
    SUPPORT_TICKET_CLOSED(HttpStatus.CONFLICT, "This ticket is closed; open a new one to continue"),

    // --- Sessions ---
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Session not found"),

    // --- Verification ---
    VERIFICATION_CODE_INVALID(HttpStatus.BAD_REQUEST, "The code is incorrect"),
    VERIFICATION_CODE_EXPIRED(HttpStatus.GONE, "This code has expired"),
    VERIFICATION_TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts, request a new code"),
    VERIFICATION_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "A code was sent recently, please wait"),
    VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "No verification is in progress"),
    VERIFICATION_REQUIRED(HttpStatus.FORBIDDEN, "This channel has not been verified yet"),
    PHONE_NUMBER_INVALID(HttpStatus.BAD_REQUEST, "Phone number is not a valid international number"),
    PHONE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Phone number is already registered"),
    DELIVERY_FAILED(HttpStatus.BAD_GATEWAY, "The verification message could not be delivered"),

    // --- Risk ---
    RISK_BLOCKED(HttpStatus.UNPROCESSABLE_ENTITY, "Transfer was blocked by the risk engine"),

    // --- Generic ---
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Request validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Request body could not be parsed"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method is not supported for this endpoint"),
    CONFLICT(HttpStatus.CONFLICT, "The request conflicts with the current state of the resource"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, please slow down"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal error occurred");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
