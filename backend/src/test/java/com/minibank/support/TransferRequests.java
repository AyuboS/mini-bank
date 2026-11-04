package com.minibank.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Builds transfer request bodies, so each test states only the field it is exercising. */
public final class TransferRequests {

    private TransferRequests() {}

    public static Map<String, Object> transfer(UUID fromAccountId, String toAccountNumber, String amount) {
        return transfer(fromAccountId, toAccountNumber, amount, "EUR");
    }

    public static Map<String, Object> transfer(
            UUID fromAccountId, String toAccountNumber, String amount, String currency) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fromAccountId", fromAccountId.toString());
        body.put("toAccountNumber", toAccountNumber);
        body.put("amount", new java.math.BigDecimal(amount));
        body.put("currency", currency);
        body.put("description", "Test transfer");
        return body;
    }

    /** A fresh idempotency key, for tests that are not about replay. */
    public static Map<String, String> newKey() {
        return Map.of("Idempotency-Key", "test-" + UUID.randomUUID());
    }

    public static Map<String, String> key(String value) {
        return Map.of("Idempotency-Key", value);
    }
}
