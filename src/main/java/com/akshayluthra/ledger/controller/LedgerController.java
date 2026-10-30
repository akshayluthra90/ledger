package com.akshayluthra.ledger.controller;

import com.akshayluthra.ledger.exception.NoRecordFoundException;
import com.akshayluthra.ledger.model.AccountResponse;
import com.akshayluthra.ledger.model.BalanceResponse;
import com.akshayluthra.ledger.model.EntryRequest;
import com.akshayluthra.ledger.model.LedgerEntry;
import com.akshayluthra.ledger.model.RecordedEntry;
import com.akshayluthra.ledger.service.LedgerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Controller class for defining all the APIs for ledger.
 */
@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    /**
     * API to create an account
     * @return 201 with the new account id, and a Location header pointing at its balance
     */
    @PostMapping("/accounts")
    public ResponseEntity<AccountResponse> createAccount() {
        String accountId = ledgerService.createAccount();

        // Points at the balance endpoint, which is the only GET an account has. The
        // bare /accounts/{id} would read better but there is no handler (for this exercise) for
        // it, so a client following this header would get a 404.
        return ResponseEntity
                .created(URI.create("/api/ledger/accounts/" + accountId + "/balance"))
                .body(new AccountResponse(accountId));
    }

    /**
     * API to deposit funds
     * @param request {@link EntryRequest}
     * @return 201 with the new {@link LedgerEntry}, or 200 with the original one
     *         if this idempotency key was already used for the same request
     */
    @PostMapping("/deposit")
    public ResponseEntity<LedgerEntry> deposit(@Valid @RequestBody EntryRequest request) {
        return buildAndRespond(ledgerService.deposit(request));
    }

    /**
     * Api to withdraw an amount from the account.
     * @param request : {@link EntryRequest}
     * @return 201 with the new {@link LedgerEntry}, or 200 with the original one
     *         if this idempotency key was already used for the same request
     */
    @PostMapping("/withdraw")
    public ResponseEntity<LedgerEntry> withdraw(@Valid @RequestBody EntryRequest request) {
        return buildAndRespond(ledgerService.withdraw(request));
    }

    /**
     * 201 Created for a new entry, 200 OK when an idempotency key was replayed
     * and nothing was created.
     */
    private static ResponseEntity<LedgerEntry> buildAndRespond(RecordedEntry recorded) {
        LedgerEntry entry = recorded.entry();

        if (!recorded.created()) {
            return ResponseEntity.ok(entry);
        }

        return ResponseEntity
                .created(URI.create("/api/ledger/ledger-entry/" + entry.transactionId()))
                .body(entry);
    }

    /**
     * Get a specific transaction
     * @param transactionId which maps the transaction we want to obtain.
     * @return the {@link LedgerEntry}, or a 404 if no such transaction exists
     */
    @GetMapping("/ledger-entry/{transactionId}")
    public LedgerEntry getTransaction(@PathVariable String transactionId) {
        LedgerEntry entry = ledgerService.getTransaction(transactionId);

        // Thrown rather than building the 404 by hand, so this one gets the same
        // ProblemDetail body as every other error instead of an empty response.
        if (entry == null) {
            throw new NoRecordFoundException(transactionId);
        }

        return entry;
    }

    /**
     * Get balance for the account
     * @param accountId for which the balance is required.
     * @return the account id and its current balance
     */
    @GetMapping("/accounts/{accountId}/balance")
    public BalanceResponse balance(@PathVariable String accountId) {
        return new BalanceResponse(accountId, ledgerService.balance(accountId));
    }

    /**
     * Transaction history for an account, oldest first.
     * @param accountId whose history is wanted.
     * @return every entry recorded against the account, or a 404 if it does not
     *         exist, so an unknown account is distinguishable from an empty one
     */
    @GetMapping("/accounts/{accountId}/transactions")
    public List<LedgerEntry> transactions(@PathVariable String accountId) {
        return ledgerService.transactionHistory(accountId);
    }
}
