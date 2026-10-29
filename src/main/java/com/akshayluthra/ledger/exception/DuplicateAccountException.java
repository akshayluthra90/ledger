package com.akshayluthra.ledger.exception;

/**
 * Exception class for instance when duplicate account is found.
 */
public class DuplicateAccountException extends IllegalArgumentException {
    public DuplicateAccountException(String userId) {
        super("Duplicate account exception : " + userId);
    }
}
