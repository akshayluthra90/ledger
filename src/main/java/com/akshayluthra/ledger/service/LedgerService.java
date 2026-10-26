package com.akshayluthra.ledger.service;

import com.akshayluthra.ledger.dao.LedgerStorage;
import com.akshayluthra.ledger.exception.AccountNotFoundException;
import com.akshayluthra.ledger.exception.DuplicateTransactionException;
import com.akshayluthra.ledger.exception.InsufficientFundsException;
import com.akshayluthra.ledger.model.EntryRequest;
import com.akshayluthra.ledger.model.EntryType;
import com.akshayluthra.ledger.model.LedgerEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * Ledger Service to service all ledger requests.
 *
 * <p>Idempotency is handled here rather than in the storage layer: the key
 * identifies a client request, not a stored row, so the logic belongs above the
 * DAO and is written once for every storage backend.
 */
@Service
public class LedgerService {

    private final LedgerStorage ledgerStorage;

    /**
     * Idempotency key to the transaction it produced. Only successful writes
     * appear here.
     */
    private final Map<String, String> idempotencyKeyTraceMap = new ConcurrentHashMap<>();

    public LedgerService(LedgerStorage ledgerStorage) {
        this.ledgerStorage = ledgerStorage;
    }

    /**
     * Opens a new account with a server-generated id.
     */
    public String createAccount() {
        return ledgerStorage.addAccount(UUID.randomUUID().toString());
    }

    public String closeAccount(String accountId) {
        return ledgerStorage.deleteAccount(accountId);
    }

    /**
     * Function to deposit money and log the transaction.
     */
    public LedgerEntry deposit(EntryRequest request) {
        return addRecord(request, EntryType.DEPOSIT);
    }

    /**
     * Function to withdraw money and log the transaction.
     */
    public LedgerEntry withdraw(EntryRequest request) {
        return addRecord(request, EntryType.WITHDRAWAL);
    }

    public BigDecimal balance(String accountId) {
        return ledgerStorage.getBalance(accountId);
    }

    public LedgerEntry getTransaction(String transactionId) {
        return ledgerStorage.getTransaction(transactionId);
    }

    /**
     * Records an entry, replaying the original result if this request has
     * already been applied.
     *
     * The key is only ever present for a write that succeeded. A rejected
     * request leaves no key behind, so the client can retry with the same one —
     * otherwise a withdrawal refused for insufficient funds would burn its key
     * and every later retry would be answered with a transaction that was never
     * recorded.
     */
    private LedgerEntry addRecord(EntryRequest request, EntryType entryType) {
        // Check if the request is valid.
        checkIfValidRequest(request);

        String idempotencyKey = request.idempotencyKey();

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            // No accountIdempotencyKey supplied. Bean Validation makes it mandatory over HTTP, but a
            // direct caller can still opt out of replay protection.
            return writeToStorage(request, entryType, getTransactionId());
        }

        String accountIdempotencyKey = request.accountId() + ":" + idempotencyKey;

        // computeIfAbsent is the check and the add in one atomic step, and it
        // runs the write inside that step. As everything happens within the compute if absent block, we dont need
        // explicitly define any removal of the accountIdempotencyKey if the transaction failed to be stored.
        String transactionId = idempotencyKeyTraceMap.computeIfAbsent(
                accountIdempotencyKey, ignored -> writeToStorage(request, entryType, getTransactionId()).transactionId());

        return ledgerStorage.getTransaction(transactionId);
    }

    private LedgerEntry writeToStorage(EntryRequest request, EntryType entryType, String transactionId) {
        LedgerEntry ledgerEntry = new LedgerEntry(
                request.accountId(),
                transactionId,
                request.description(),
                request.amount(),
                Instant.now());

        try {
            ledgerStorage.addRecord(ledgerEntry, entryType);
        } catch (DuplicateTransactionException | AccountNotFoundException ex) {
            throw new RuntimeException("Internal storage error : " + transactionId, ex);
        } catch (InsufficientFundsException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Internal storage exception : " + transactionId, ex);
        }

        return ledgerEntry;
    }

    private void checkIfValidRequest(EntryRequest request) {
        if (request.accountId() == null || request.accountId().isBlank()) {
            throw new IllegalArgumentException("Invalid request, invalid account id");
        }

        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid request, invalid amount");
        }
    }

    private String getTransactionId() {
        return UUID.randomUUID().toString();
    }
}
