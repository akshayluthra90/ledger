package com.akshayluthra.ledger.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.akshayluthra.ledger.exception.AccountNotFoundException;
import com.akshayluthra.ledger.exception.InsufficientFundsException;
import com.akshayluthra.ledger.model.EntryRequest;
import com.akshayluthra.ledger.service.LedgerService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Checks that service exceptions reach the client as the intended HTTP status.
 *
 * <p>{@link LedgerExceptionHandler} is never referenced by name here, or
 * anywhere else. It is found by component scan and invoked by the framework —
 * these assertions are what proves that wiring actually happened.
 */
@WebMvcTest(LedgerController.class)
class LedgerControllerTest {

    private static final String DEPOSIT_BODY = """
            {"idempotencyKey":"key-1","description":"test","accountId":"acc-1","amount":10.00}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LedgerService ledgerService;

    @Test
    void unknownAccountBecomesNotFound() throws Exception {
        given(ledgerService.deposit(any(EntryRequest.class)))
                .willThrow(new AccountNotFoundException("acc-1"));

        mockMvc.perform(post("/api/ledger/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DEPOSIT_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No account found with the id : acc-1"));
    }

    @Test
    void insufficientFundsBecomesUnprocessable() throws Exception {
        given(ledgerService.withdraw(any(EntryRequest.class)))
                .willThrow(new InsufficientFundsException("acc-1"));

        mockMvc.perform(post("/api/ledger/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DEPOSIT_BODY))
                .andExpect(status().isUnprocessableEntity());
    }

    /**
     * Not a handler case: Bean Validation rejects the body before the
     * controller method runs, and Spring's own advice turns that into a 400.
     */
    @Test
    void blankIdempotencyKeyIsRejectedBeforeTheService() throws Exception {
        mockMvc.perform(post("/api/ledger/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"","description":"test","accountId":"acc-1","amount":10.00}
                                """))
                .andExpect(status().isBadRequest());
    }
}
