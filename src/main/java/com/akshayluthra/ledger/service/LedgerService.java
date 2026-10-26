package com.akshayluthra.ledger.service;

import com.akshayluthra.ledger.model.LedgerEntry;
import com.akshayluthra.ledger.model.EntryRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.Nonnull;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

/**
 * Ledger Service to service all ledger requests.
 */
@Service
public class LedgerService {

    private final Map<String, LedgerEntry> entries = new ConcurrentHashMap<>();

    public LedgerEntry deposit(EntryRequest request) {
        LedgerEntry ledgerEntry = new LedgerEntry(
                UUID.randomUUID().toString(),
                request.description(),
                request.amount(),
                Instant.now());
        entries.put(ledgerEntry.id(), ledgerEntry);
        return ledgerEntry;
    }

    public BigDecimal balance() {
        return entries.values().stream()
                .map(LedgerEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean delete(String id) {
        return true;
    }
}
