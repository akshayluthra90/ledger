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
 * <p>Package-private on purpose: this is the in-memory storage mechanism, not a
 * domain type. A JDBC {@link LedgerStorage} would have rows and a SQL
 * transaction instead, with no equivalent of this object.
 *
 * <p>Every method synchronizes on the instance, and
 * {@link InMemoryLocalStorage} synchronizes on the same monitor to hold the
 * overdraft check and the balance update together as one atomic step. Keeping
 * the class inside {@code dao} keeps that monitor unreachable from the rest of
 * the application.
 *
 * <p>The entry list is a plain {@link ArrayList} rather than a concurrent one on
 * purpose: it is only ever touched while the monitor is held, so a concurrent
 * collection would add cost without adding safety.
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
