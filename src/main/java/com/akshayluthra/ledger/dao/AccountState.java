package com.akshayluthra.ledger.dao;

import com.akshayluthra.ledger.model.EntryType;
import com.akshayluthra.ledger.model.LedgerEntry;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable state for a single account: its running balance, the ids of the
 * entries that produced that balance.
 *
 * Package-private on purpose: this is the in-memory storage mechanism, not a
 * domain type. A JDBC {@link LedgerStorage} would have rows and a SQL
 * transaction instead, with no equivalent of this object.
 */
final class AccountState {

    /**
     * Ids of the entries applied to this account, oldest first.
     */
    private final List<String> transactionIds = new ArrayList<>();

    private BigDecimal balance = BigDecimal.ZERO;

    synchronized BigDecimal balance() {
        return balance;
    }

    synchronized List<String> transactionIds() {
        return List.copyOf(transactionIds);
    }

    /**
     * Records an entry against this account and moves the balance in the
     * direction the entry carries.
     *
     * @param ledgerEntry the entry being recorded
     */
    synchronized void apply(LedgerEntry ledgerEntry) {
        transactionIds.add(ledgerEntry.transactionId());
        balance = (ledgerEntry.entryType() == EntryType.WITHDRAWAL)
                ? balance.subtract(ledgerEntry.amount())
                : balance.add(ledgerEntry.amount());
    }
}
