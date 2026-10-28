package com.akshayluthra.ledger.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.akshayluthra.ledger.exception.AccountNotFoundException;
import com.akshayluthra.ledger.exception.InsufficientFundsException;
import com.akshayluthra.ledger.model.EntryRequest;
import com.akshayluthra.ledger.model.EntryType;
import com.akshayluthra.ledger.model.LedgerEntry;
import com.akshayluthra.ledger.service.LedgerService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests for {@link LedgerService}
 * Checks that service exceptions reach the client as the intended HTTP status.
 */
@WebMvcTest(LedgerController.class)
class LedgerControllerTest {

    private static final String TEST_ACCOUNT_ID = "test_account_id";
    private static final String TEST_TXN_1 = "test_transaction_1";
    private static final String TEST_TXN_2 = "test_transaction_2";

    private static final String DEPOSIT_BODY = """
            {"idempotencyKey":"key-1","description":"test","accountId":"test_account_id","amount":10.00}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LedgerService ledgerService;

    @Test
    void unknownAccountReturnsNoAccountFound() throws Exception {
        given(ledgerService.deposit(any(EntryRequest.class)))
                .willThrow(new AccountNotFoundException(TEST_ACCOUNT_ID));

        mockMvc.perform(post("/api/ledger/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DEPOSIT_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No account found with the id : " + TEST_ACCOUNT_ID));
    }

    @Test
    void insufficientFundsTransactionFailed() throws Exception {
        given(ledgerService.withdraw(any(EntryRequest.class)))
                .willThrow(new InsufficientFundsException(TEST_ACCOUNT_ID));

        mockMvc.perform(post("/api/ledger/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DEPOSIT_BODY))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void historyIsReturnedOldestFirst() throws Exception {
        given(ledgerService.transactionHistory(TEST_ACCOUNT_ID)).willReturn(List.of(
                new LedgerEntry(TEST_ACCOUNT_ID, TEST_TXN_1, EntryType.DEPOSIT,
                        "first", new BigDecimal("100.00"), Instant.EPOCH),
                new LedgerEntry(TEST_ACCOUNT_ID, TEST_TXN_2, EntryType.WITHDRAWAL,
                        "second", new BigDecimal("40.00"), Instant.EPOCH)));

        mockMvc.perform(get("/api/ledger/accounts/" + TEST_ACCOUNT_ID + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].transactionId").value(TEST_TXN_1))
                .andExpect(jsonPath("$[1].transactionId").value(TEST_TXN_2))
                // Direction is on the wire, so same-value entries are distinguishable.
                .andExpect(jsonPath("$[0].entryType").value("DEPOSIT"))
                .andExpect(jsonPath("$[1].entryType").value("WITHDRAWAL"));
    }

    @Test
    void balanceOfUnknownAccountThrowsAccountNotFound() throws Exception {
        given(ledgerService.balance(TEST_ACCOUNT_ID))
                .willThrow(new AccountNotFoundException(TEST_ACCOUNT_ID));

        mockMvc.perform(get("/api/ledger/accounts/" + TEST_ACCOUNT_ID + "/balance"))
                .andExpect(status().isNotFound());
    }

    @Test
    void historyOfUnknownAccountThrowsAccountNotFound() throws Exception {
        given(ledgerService.transactionHistory(TEST_ACCOUNT_ID))
                .willThrow(new AccountNotFoundException(TEST_ACCOUNT_ID));

        mockMvc.perform(get("/api/ledger/accounts/" + TEST_ACCOUNT_ID + "/transactions"))
                .andExpect(status().isNotFound());
    }

    /**
     * Not a handler case: Bean Validation rejects the body before the
     * controller method runs, and Spring's own advice turns that into a 400.
     */
    @Test
    void blankIdempotencyKeyRejected() throws Exception {
        mockMvc.perform(post("/api/ledger/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"","description":"test","accountId":"test-acc","amount":10.00}
                                """))
                .andExpect(status().isBadRequest());
    }
}
