package com.akshayluthra.ledger.controller;

import com.akshayluthra.ledger.model.EntryRequest;
import com.akshayluthra.ledger.model.LedgerEntry;
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
        LedgerEntry created = ledgerService.deposit(request);
        return ResponseEntity
                .created(URI.create("/api/ledger/ledger-entry/" + created.transactionId()))
                .body(created);
    }

    /**
     * Api to withdraw an amount from the account.
     * @param request : {@link EntryRequest}
     * @return {@link LedgerEntry}
     */
    @PostMapping("/withdraw")
    public ResponseEntity<LedgerEntry> withdraw(@Valid @RequestBody EntryRequest request) {
        LedgerEntry created = ledgerService.withdraw(request);
        return ResponseEntity
                .created(URI.create("/api/ledger/ledger-entry/" + created.transactionId()))
                .body(created);
    }

    /**
     * Get a specific transaction
      * @param transactionId which maps the transaction we want to obtain.
     * @return {@link LedgerEntry}
     */
    @GetMapping("/ledger-entry/{transactionId}")
    public ResponseEntity<LedgerEntry> getTransaction(@PathVariable String transactionId) {
        LedgerEntry entry = ledgerService.getTransaction(transactionId);
        return (entry == null)
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(entry);
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
