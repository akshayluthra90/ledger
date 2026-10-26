package com.akshayluthra.ledger.exception;

public class NoRecordFoundException extends RuntimeException {
    public NoRecordFoundException(String id) {
        super("No record found exception : " + id);
    }
}
