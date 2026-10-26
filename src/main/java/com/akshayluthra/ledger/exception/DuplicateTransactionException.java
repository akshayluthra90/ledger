package com.akshayluthra.ledger.exception;

public class DuplicateTransactionException extends IllegalArgumentException {
    public DuplicateTransactionException(String id) {
        super("Duplicate transaction exception : " + id);
    }
}
