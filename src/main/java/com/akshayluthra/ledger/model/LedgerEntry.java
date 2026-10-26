package com.akshayluthra.ledger.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single ledger entry. A record works well here: Jackson serialises the
 * accessors to JSON and deserialises via the canonical constructor.
 */
public record LedgerEntry(
        String id,
        String description,
        BigDecimal amount,
        Instant createdAt) {
}
