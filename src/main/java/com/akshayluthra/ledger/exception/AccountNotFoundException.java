package com.akshayluthra.ledger.exception;

/**
 * Exception class for instance when Account is not found.
 */
public class AccountNotFoundException extends IllegalArgumentException {
    public AccountNotFoundException(String userId) {
        super("No account found with the id : " + userId);
    }
}
