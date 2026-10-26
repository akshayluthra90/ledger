package com.akshayluthra.ledger.exception;

public class AccountNotFoundException extends IllegalArgumentException {
    public AccountNotFoundException(String userId) {
        super("No account found with the id : " + userId);
    }
}
