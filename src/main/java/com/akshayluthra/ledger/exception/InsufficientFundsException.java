package com.akshayluthra.ledger.exception;

public class InsufficientFundsException extends IllegalArgumentException {
    public InsufficientFundsException(String userId) {
        super("Insufficient funds exception : " + userId);
    }
}
