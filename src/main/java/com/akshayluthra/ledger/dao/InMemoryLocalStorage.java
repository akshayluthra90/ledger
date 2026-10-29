package com.akshayluthra.ledger.dao;

import com.akshayluthra.ledger.exception.*;
import com.akshayluthra.ledger.model.EntryType;
import com.akshayluthra.ledger.model.LedgerEntry;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * In memory local storage class for storing data in memory.
 *
 * Two maps are held, but they play different roles. {@code transactionStorage}
 * is the source of truth: one entry per transaction id.
 *
 * The per-account state lives in {@link AccountState}, and that object's
 * monitor provides the write lock on this.
 */
@Repository
public class InMemoryLocalStorage implements LedgerStorage {

    /**
     * Map to store transactions keyed by transactionId.
     */
    private final Map<String, LedgerEntry> transactionStorage;

    /**
     * Map to store account state keyed by accountId.
     */
    private final Map<String, AccountState> accountStorage;

    public InMemoryLocalStorage() {
        transactionStorage = new ConcurrentHashMap<>();
        accountStorage = new ConcurrentHashMap<>();
    }

    @Override
    public LedgerEntry getTransaction(String id) {
        return transactionStorage.get(id);
    }

    @Override
    public String addRecord(LedgerEntry ledgerEntry) {
        return post(ledgerEntry);
    }

    @Override
    public BigDecimal getBalance(String accountId) {
        AccountState account = accountStorage.get(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }
        return account.balance();
    }

    @Override
    public String addAccount(String accountId) {
        if (accountStorage.putIfAbsent(accountId, new AccountState()) != null) {
            throw new DuplicateAccountException(accountId);
        }
        return accountId;
    }


    /**
     * This function currently doesn't add thread safety in terms of the whole account and there can be a case
     * that there is a race condition in someone adding or removing funds and the delete account operation.
     * This is safe in the current context as we are not adding operations like moving money when closing/deleting
     * an account.
     * @param accountId : For the account that needs to be deleted.
     * @return AccountId of the deleted account.
     */
    @Override
    public String deleteAccount(String accountId) {
        // We are ok to not add thread safety here, as the worst case here would be
        // that the account is tried to be deleted twice and the map would just return a null, hence it is completely
        // safe.
        if(!accountStorage.containsKey(accountId)) {
            throw new NoRecordFoundException(accountId);
        }

        accountStorage.remove(accountId);
        return accountId;
    }

    /**
     * All entries for an account, oldest first. Empty if the account has none.
     * The ids held on the account are resolved against
     * {@link #transactionStorage} here.
     *
     * <p>Resolving happens outside the monitor, which is safe because
     * {@link #post} writes the entry to {@code transactionStorage} before adding
     * its id to the account. Any id visible in that list therefore already has
     * an entry behind it.
     */
    @Override
    public List<LedgerEntry> getRecordsForAccount(String accountId) {
        AccountState account = accountStorage.get(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }

        List<String> transactionIds = account.transactionIds();
        List<LedgerEntry> entries = new ArrayList<>(transactionIds.size());
        for (String transactionId : transactionIds) {
            entries.add(transactionStorage.get(transactionId));
        }
        return List.copyOf(entries);
    }

    /**
     * Applies a deposit or a withdrawal.
     *
     * <p>Order inside the lock matters: every rejection happens before anything
     * is written, so a rejected request leaves no trace.
     *
     * @return the id of the recorded transaction
     */
    private String post(LedgerEntry ledgerEntry) {
        // The amount should be positive for transaction to be valid.
        if (ledgerEntry.amount() == null || ledgerEntry.amount().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be positive : " + ledgerEntry.transactionId());
        }

        // One lookup, not containsKey-then-get: the latter leaves a window in
        // which deleteAccount can remove the account between the two calls,
        // leaving a null to synchronize on.
        AccountState account = accountStorage.get(ledgerEntry.accountId());
        if (account == null) {
            throw new AccountNotFoundException(ledgerEntry.accountId());
        }

        synchronized (account) {
            // Throw an exception if sufficient funds are not available.
            if (ledgerEntry.entryType() == EntryType.WITHDRAWAL
                    && account.balance().compareTo(ledgerEntry.amount()) < 0) {
                throw new InsufficientFundsException(ledgerEntry.accountId());
            }

            if (transactionStorage.putIfAbsent(ledgerEntry.transactionId(), ledgerEntry) != null) {
                throw new DuplicateTransactionException(ledgerEntry.transactionId());
            }

            account.apply(ledgerEntry);
        }

        return ledgerEntry.transactionId();
    }
}
