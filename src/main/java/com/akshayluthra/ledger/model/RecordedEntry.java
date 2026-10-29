package com.akshayluthra.ledger.model;

/**
 * The outcome of recording an entry: the entry itself, plus whether this call
 * is what created it.
 *
 * Needed because a replay and a first write are indistinguishable from the
 * entry alone, and they are not the same HTTP result. A first write is a 201
 * Created; a replay created nothing and is a 200 OK.
 */
public record RecordedEntry(LedgerEntry entry, boolean created) {
}
