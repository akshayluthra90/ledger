package com.akshayluthra.ledger.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Inbound payload. Kept separate from {@link LedgerEntry} so clients cannot set
 * server-owned fields like {@code transactionId} or {@code createdAt}.
 *
 * Every field is bounded and validated. {@code BigDecimal} is arbitrary-precision, so
 * without {@link Digits} a request can carry an amount with a thousand digits
 * or a fraction of a cent, and without {@link Size} the strings are a way to
 * push unbounded data into a store that never expires.
 */
public record EntryRequest(
        @NotBlank(message = "idempotency key cannot be blank")
        @Size(max = 64, message = "idempotency key must be at most 64 characters")
        String idempotencyKey,

        @NotBlank(message = "description must not be blank")
        @Size(max = 256, message = "description must be at most 256 characters")
        String description,

        @NotBlank(message = "accountId must not be blank")
        String accountId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
        @Digits(integer = 15, fraction = 2,
                message = "amount must have at most 15 digits and 2 decimal places")
        BigDecimal amount) {
}
