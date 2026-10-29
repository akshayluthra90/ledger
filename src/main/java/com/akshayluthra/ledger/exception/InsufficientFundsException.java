package com.akshayluthra.ledger.exception;

/**
 * Exception class for instance when there are insufficient funds.
 */
public class InsufficientFundsException extends IllegalArgumentException {
    public InsufficientFundsException(String userId) {
        super("Insufficient funds exception : " + userId);
    }
}
