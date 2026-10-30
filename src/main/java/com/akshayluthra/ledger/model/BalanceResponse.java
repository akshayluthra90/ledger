package com.akshayluthra.ledger.model;

import java.math.BigDecimal;

/**
 * Response object for a balance lookup.
 *
 * An object rather than the bare number, so a client does not have to guess
 * what the value means, and so a currency or an as-of timestamp can be added
 * later as new fields as well.
 *
 * @param accountId the account the balance belongs to
 * @param balance   the account's current balance
 */
public record BalanceResponse(String accountId, BigDecimal balance) {
}
