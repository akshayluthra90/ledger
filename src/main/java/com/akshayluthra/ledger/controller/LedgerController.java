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

    @PostMapping
    public ResponseEntity<LedgerEntry> deposit(@Valid @RequestBody EntryRequest request) {
        LedgerEntry created = ledgerService.deposit(request);
        return ResponseEntity
                .created(URI.create("/api/ledger/" + created.id()))
                .body(created);
    }

    @GetMapping("/ledger-entry/{id}")
    public ResponseEntity<LedgerEntry> get(@PathVariable String id) {
        return null;
    }


    @DeleteMapping("/ledger-entry/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return ledgerService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/balance")
    public BigDecimal balance() {
        return ledgerService.balance();
    }
}
