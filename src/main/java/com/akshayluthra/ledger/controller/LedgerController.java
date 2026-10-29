package com.akshayluthra.ledger.controller;

import com.akshayluthra.ledger.exception.NoRecordFoundException;
import com.akshayluthra.ledger.model.EntryRequest;
import com.akshayluthra.ledger.model.LedgerEntry;
import com.akshayluthra.ledger.model.RecordedEntry;
import com.akshayluthra.ledger.service.LedgerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
     * @return the account id
     */
    @PostMapping("/accounts")
    public ResponseEntity<String> createAccount() {
        String accountId = ledgerService.createAccount();
        return ResponseEntity
                .created(URI.create("/api/ledger/accounts/" + accountId))
                .body(accountId);
    }

    /**
     * API to deposit funds
     * @param request {@link EntryRequest}
     * @return {@link LedgerEntry}
     */
    @PostMapping("/deposit")
    public ResponseEntity<LedgerEntry> deposit(@Valid @RequestBody EntryRequest request) {
        return respond(ledgerService.deposit(request));
    }

    /**
     * Api to withdraw an amount from the account.
     * @param request : {@link EntryRequest}
     * @return {@link LedgerEntry}
     */
    @PostMapping("/withdraw")
    public ResponseEntity<LedgerEntry> withdraw(@Valid @RequestBody EntryRequest request) {
        return respond(ledgerService.withdraw(request));
    }

    /**
     * 201 Created for a new entry, 200 OK when an idempotency key was replayed
     * and nothing was created.
     */
    private static ResponseEntity<LedgerEntry> respond(RecordedEntry recorded) {
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
     * @return {@link LedgerEntry}
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
     * @return balance
     */
    @GetMapping("/accounts/{accountId}/balance")
    public BigDecimal balance(@PathVariable String accountId) {
        return ledgerService.balance(accountId);
    }

    /**
     * Transaction history for an account, oldest first. 404 if the account does
     * not exist, so an unknown account is distinguishable from an empty one.
     */
    @GetMapping("/accounts/{accountId}/transactions")
    public List<LedgerEntry> transactions(@PathVariable String accountId) {
        return ledgerService.transactionHistory(accountId);
    }
}
