package com.akshayluthra.ledger.controller;

import com.akshayluthra.ledger.model.EntryRequest;
import com.akshayluthra.ledger.model.LedgerEntry;
import com.akshayluthra.ledger.service.LedgerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping("/accounts")
    public ResponseEntity<String> createAccount() {
        String accountId = ledgerService.createAccount();
        return ResponseEntity
                .created(URI.create("/api/ledger/accounts/" + accountId))
                .body(accountId);
    }

    @PostMapping("/deposit")
    public ResponseEntity<LedgerEntry> deposit(@Valid @RequestBody EntryRequest request) {
        LedgerEntry created = ledgerService.deposit(request);
        return ResponseEntity
                .created(URI.create("/api/ledger/ledger-entry/" + created.transactionId()))
                .body(created);
    }

    @PostMapping("/withdraw")
    public ResponseEntity<LedgerEntry> withdraw(@Valid @RequestBody EntryRequest request) {
        LedgerEntry created = ledgerService.withdraw(request);
        return ResponseEntity
                .created(URI.create("/api/ledger/ledger-entry/" + created.transactionId()))
                .body(created);
    }

    @GetMapping("/ledger-entry/{transactionId}")
    public ResponseEntity<LedgerEntry> get(@PathVariable String transactionId) {
        LedgerEntry entry = ledgerService.getTransaction(transactionId);
        return (entry == null)
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(entry);
    }

    @GetMapping("/accounts/{accountId}/balance")
    public BigDecimal balance(@PathVariable String accountId) {
        return ledgerService.balance(accountId);
    }
}
