package com.akshayluthra.ledger.exception;

/**
 * Exception class for instance when a duplicate transaction is received.
 */
public class DuplicateTransactionException extends IllegalArgumentException {
    public DuplicateTransactionException(String id) {
        super("Duplicate transaction exception : " + id);
    }
}
