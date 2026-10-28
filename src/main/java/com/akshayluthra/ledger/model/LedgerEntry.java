package com.akshayluthra.ledger.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single ledger entry. A record works well here: Jackson serialises the
 * accessors to JSON and deserialises via the canonical constructor.
 *
 * <p>{@code amount} is always positive; {@code entryType} carries the direction.
 * Without it a history response shows a deposit and a withdrawal of the same
 * value identically.
 */
public record LedgerEntry(
        String accountId,
        String transactionId,
        EntryType entryType,
        String description,
        BigDecimal amount,
        Instant createdAt) {
}

