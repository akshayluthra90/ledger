package com.akshayluthra.ledger.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A class defining a single ledger entry.
 *
 * {@code amount} is always positive;
 * {@code entryType} carries the direction. Without it a history response shows a deposit and a withdrawal of the same
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

