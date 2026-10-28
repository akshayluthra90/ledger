package com.akshayluthra.ledger.service;

import com.akshayluthra.ledger.dao.LedgerStorage;
import com.akshayluthra.ledger.exception.DuplicateAccountException;
import com.akshayluthra.ledger.exception.DuplicateTransactionException;
import com.akshayluthra.ledger.model.EntryRequest;
import com.akshayluthra.ledger.model.EntryType;
import com.akshayluthra.ledger.model.LedgerEntry;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ledger Service to service all ledger requests.
 * <p>
 * Note : Idempotency is handled here rather than in the storage layer because the key
 * identifies a client request, not a stored row, so the logic belongs above the
 * DAO layer we created and is written once.
 */
@Service
public class LedgerService {

    /**
     * Cents is the finest currency granularity supported.
     */
    private static final int MAX_MINOR_UNIT_DIGITS = 2;

    /**
     * Digits before the decimal point, so the largest amount is just under 10^15.
     */
    private static final int MAX_MAJOR_UNIT_DIGITS = 15;

    private static final int MAX_DESCRIPTION_LENGTH = 256;

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

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
        String accountId = UUID.randomUUID().toString();

        try {
            return ledgerStorage.addAccount(accountId);
        } catch (DuplicateAccountException ex) {
            // The caller did not choose this id, so a collision is not a conflict
            // they can resolve — it is a server fault, same as a duplicate
            // transaction id. We can always add additional retries but for this app purpose the UUID function
            // should be good enough and shouldnt be producing the collisions.
            throw new RuntimeException("Internal storage error : " + accountId, ex);
        }
    }

    public String closeAccount(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Invalid request, invalid account id");
        }
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
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Invalid request, invalid account id");
        }
        return ledgerStorage.getBalance(accountId);
    }


    public LedgerEntry getTransaction(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Invalid request, invalid transaction id");
        }
        return ledgerStorage.getTransaction(transactionId);
    }

    /**
     * Every entry recorded against an account, oldest first.
     */
    public List<LedgerEntry> transactionHistory(String accountId) {
        return ledgerStorage.getRecordsForAccount(accountId);
    }

    /**
     * Records an entry, replaying the original result if this request has
     * already been applied.
     * <p>
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

        String accountIdempotencyKey = request.accountId() + ":" + idempotencyKey;

        // computeIfAbsent is the check and the add in one atomic step, and it
        // runs the write inside that step. As everything happens within the compute if absent block, we dont need
        // explicitly define any removal of the accountIdempotencyKey if the transaction failed to be stored.
        String transactionId = idempotencyKeyTraceMap.computeIfAbsent(
                accountIdempotencyKey, ignored -> writeToStorage(request, entryType, getTransactionId()).transactionId());

        LedgerEntry originalEntry = ledgerStorage.getTransaction(transactionId);
        checkReplayMatchesOriginal(request, entryType, originalEntry);

        return originalEntry;
    }

    /**
     * Rejects a key that is being reused for a different request.
     * <p>
     * A replay has to be the same request, otherwise the caller is told their
     * transaction succeeded and handed a receipt for an entirely different one —
     * a withdrawal request answered with an earlier deposit, for instance.
     * <p>
     * Note : On a first write this compares the entry against the request that just
     * produced it, which always matches. That keeps one code path instead of unnecessary
     * branching on whether the key was already present.
     */
    private void checkReplayMatchesOriginal(
            EntryRequest request, EntryType entryType, LedgerEntry originalEntry) {
        boolean sameRequest = originalEntry.entryType() == entryType
                && originalEntry.amount().compareTo(request.amount()) == 0
                && Objects.equals(originalEntry.description(), request.description());

        if (!sameRequest) {
            throw new IllegalArgumentException(
                    "Idempotency key reused with a different request : " + request.idempotencyKey());
        }
    }

    private LedgerEntry writeToStorage(EntryRequest request, EntryType entryType, String transactionId) {
        LedgerEntry ledgerEntry = new LedgerEntry(
                request.accountId(),
                transactionId,
                entryType,
                request.description(),
                request.amount(),
                Instant.now());

        try {
            ledgerStorage.addRecord(ledgerEntry);
        } catch (DuplicateTransactionException ex) {
            // A server-generated id collided, which is our limitation here and not something
            // the caller can control. Everything else — unknown account, bad
            // amount, insufficient funds — describes the request and propagates
            // unchanged so the exception handler can map it to its own status.
            throw new RuntimeException("Internal storage error : " + transactionId, ex);
        }

        return ledgerEntry;
    }

    /**
     * Bounds every field the caller controls.
     *
     * <p>This duplicates the Bean Validation constraints on {@link EntryRequest}
     * on purpose. Those only run for requests arriving over HTTP; a direct
     * caller — including these tests — bypasses them entirely, and the limits
     * below are the kind that corrupt stored data rather than just the response.
     */
    private void checkIfValidRequest(EntryRequest request) {
        if (request.accountId() == null || request.accountId().isBlank()) {
            throw new IllegalArgumentException("Invalid request, invalid account id");
        }

        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid request, invalid amount");
        }

        // Ensuring that the amount is not less than a cent.
        if (request.amount().scale() > MAX_MINOR_UNIT_DIGITS) {
            throw new IllegalArgumentException(
                    "Invalid request, amount cannot be finer than a cent : " + request.amount());
        }

        // Only support an amount up to 15 digits.
        if (request.amount().precision() - request.amount().scale() > MAX_MAJOR_UNIT_DIGITS) {
            throw new IllegalArgumentException(
                    "Invalid request, amount is too large : " + request.amount());
        }

        // Adding a limit on the number of chars as description that we are accepting.
        if (request.description() != null && request.description().length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Invalid request, description is too long");
        }

        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("Invalid request, idempotency key is mandatory.");
        }

        // Check on the idempotency key length to ensure we are not ingesting a large key, which might fill up the
        // storage.
        if (request.idempotencyKey().length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException("Invalid request, idempotency key is too long");
        }
    }

    private String getTransactionId() {
        return UUID.randomUUID().toString();
    }
}
