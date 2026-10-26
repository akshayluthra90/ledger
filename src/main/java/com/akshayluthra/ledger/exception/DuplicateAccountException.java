package com.akshayluthra.ledger.exception;

public class DuplicateAccountException extends IllegalArgumentException {
    public DuplicateAccountException(String userId) {
        super("Duplicate account exception : " + userId);
    }
}
