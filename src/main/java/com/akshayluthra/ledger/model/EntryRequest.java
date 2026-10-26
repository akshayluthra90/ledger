package com.akshayluthra.ledger.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Inbound payload. Kept separate from {@link LedgerEntry} so clients cannot set
 * server-owned fields like {@code id} or {@code createdAt}.
 */
public record EntryRequest(
        @NotBlank(message = "description must not be blank")
        String description,

        @NotNull(message = "amount is required")
        BigDecimal amount) {
}
