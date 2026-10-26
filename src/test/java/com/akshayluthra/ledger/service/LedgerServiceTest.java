package com.akshayluthra.ledger.service;

import com.akshayluthra.ledger.model.EntryRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class for {@link LedgerService}
 */
class LedgerServiceTest {

    private final LedgerService service = new LedgerService();

    @Test
    void balanceSumsEntries() {
        service.deposit(new EntryRequest("Salary", new BigDecimal("1000.00")));
        service.deposit(new EntryRequest("Rent", new BigDecimal("-650.00")));

        assertThat(service.balance()).isEqualByComparingTo("350.00");
    }

}
