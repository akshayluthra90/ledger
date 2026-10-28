package com.akshayluthra.ledger.service;

import com.akshayluthra.ledger.dao.InMemoryLocalStorage;
import com.akshayluthra.ledger.dao.LedgerStorage;
import com.akshayluthra.ledger.exception.AccountNotFoundException;
import com.akshayluthra.ledger.exception.DuplicateAccountException;
import com.akshayluthra.ledger.exception.InsufficientFundsException;
import com.akshayluthra.ledger.model.EntryRequest;
import com.akshayluthra.ledger.model.LedgerEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Test class for {@link LedgerService}
 */
class LedgerServiceTest {

    private static final String IDEMPOTENCY_KEY_1 = "idempotency_key_1";
    private static final String IDEMPOTENCY_KEY_2 = "idempotency_key_2";
    private static final String INVALID_ACCOUNT = "invalid_account";

    private final LedgerService service = new LedgerService(new InMemoryLocalStorage());

    private static EntryRequest request(String accountId, String amount, String idempotencyKey) {
        return new EntryRequest(idempotencyKey, "test entry", accountId, new BigDecimal(amount));
    }


    private static EntryRequest request(String accountId, String amount) {
        return request(accountId, amount, UUID.randomUUID().toString());
    }

    @Test
    void checkBalanceAfterDepositsAndWithdrawals() {
        String accountId = service.createAccount();

        service.deposit(request(accountId, "1000.00", IDEMPOTENCY_KEY_1));
        service.withdraw(request(accountId, "650.00", IDEMPOTENCY_KEY_2));

        assertThat(service.balance(accountId)).isEqualByComparingTo("350.00");
    }

    @Test
    void withdrawalMoreThanBalanceThrowsException() {
        String accountId = service.createAccount();
        service.deposit(request(accountId, "100.00"));

        assertThatThrownBy(() -> service.withdraw(request(accountId, "500.00")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(service.balance(accountId)).isEqualByComparingTo("100.00");
    }

    @Test
    void depositToUnknownAccountIsRejected() {
        assertThatThrownBy(() -> service.deposit(request(INVALID_ACCOUNT, "10.00")))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void missingIdempotencyKeyIsRejected() {
        String accountId = service.createAccount();

        // Mandatory even for direct callers, so nothing can write without replay
        // protection. Bean Validation only covers the HTTP path.
        assertThatThrownBy(() -> service.deposit(request(accountId, "10.00", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotency key is mandatory");

        assertThatThrownBy(() -> service.deposit(request(accountId, "10.00", "   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotency key is mandatory");

        assertThat(service.transactionHistory(accountId)).isEmpty();
    }

    @Test
    void repeatedKeyReplaysTheOriginalAndAppliesOnce() {
        String accountId = service.createAccount();

        LedgerEntry first = service.deposit(request(accountId, "100.00", IDEMPOTENCY_KEY_1));
        LedgerEntry replay = service.deposit(request(accountId, "100.00", IDEMPOTENCY_KEY_1));

        assertThat(replay.transactionId()).isEqualTo(first.transactionId());
        assertThat(service.balance(accountId)).isEqualByComparingTo("100.00");
    }

    @Test
    void sameKeyWithADifferentAmountIsRejected() {
        String accountId = service.createAccount();
        service.deposit(request(accountId, "10.00", IDEMPOTENCY_KEY_1));

        assertThatThrownBy(() -> service.deposit(request(accountId, "5000.00", IDEMPOTENCY_KEY_1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reused with a different request");

        assertThat(service.balance(accountId)).isEqualByComparingTo("10.00");
    }

    @Test
    void sameKeyUsedOnDepositThenWithdrawalShouldBeRejected() {
        String accountId = service.createAccount();
        service.deposit(request(accountId, "10.00", IDEMPOTENCY_KEY_1));

        // Previously this returned 201 with the earlier DEPOSIT entry, so a
        // withdrawal request was answered with a deposit receipt.
        assertThatThrownBy(() -> service.withdraw(request(accountId, "10.00", IDEMPOTENCY_KEY_1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reused with a different request");

        assertThat(service.balance(accountId)).isEqualByComparingTo("10.00");
    }

    @Test
    void sameKeyWithADifferentDescriptionShouldBeRejected() {
        String accountId = service.createAccount();
        service.deposit(request(accountId, "10.00", IDEMPOTENCY_KEY_1));

        EntryRequest relabelled = new EntryRequest(
                IDEMPOTENCY_KEY_1, "something else", accountId, new BigDecimal("10.00"));

        assertThatThrownBy(() -> service.deposit(relabelled))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reused with a different request");
    }

    @Test
    void replayIsNotRejectedForAmountScaleAlone() {
        String accountId = service.createAccount();
        LedgerEntry first = service.deposit(request(accountId, "10.00", IDEMPOTENCY_KEY_1));

        // 10.0 and 10.00 are the same amount, so this is a genuine retry.
        LedgerEntry replay = service.deposit(request(accountId, "10.0", IDEMPOTENCY_KEY_1));

        assertThat(replay.transactionId()).isEqualTo(first.transactionId());
        assertThat(service.balance(accountId)).isEqualByComparingTo("10.00");
    }

    @Test
    void sameKeyOnDifferentAccountsDoesNotCollideAndIsAccepted() {
        String first = service.createAccount();
        String second = service.createAccount();

        service.deposit(request(first, "10.00", IDEMPOTENCY_KEY_1));
        service.deposit(request(second, "25.00", IDEMPOTENCY_KEY_1));

        assertThat(service.balance(first)).isEqualByComparingTo("10.00");
        assertThat(service.balance(second)).isEqualByComparingTo("25.00");
    }

    @Test
    void rejectedRequestDoesNotStoreTheKeyAndCanBeRetriedWithTheSameKey() {
        String accountId = service.createAccount();
        service.deposit(request(accountId, "50.00"));

        assertThatThrownBy(() -> service.withdraw(request(accountId, "999.00", IDEMPOTENCY_KEY_2)))
                .isInstanceOf(InsufficientFundsException.class);

        // The same key must still be usable, otherwise every later retry would
        // be answered with a transaction that was never recorded.
        LedgerEntry retried = service.withdraw(request(accountId, "20.00", IDEMPOTENCY_KEY_2));

        assertThat(retried).isNotNull();
        assertThat(service.balance(accountId)).isEqualByComparingTo("30.00");
    }

    @Test
    void historyListsEveryEntryOldestFirst() {
        String accountId = service.createAccount();

        service.deposit(request(accountId, "100.00"));
        service.withdraw(request(accountId, "40.00"));
        service.deposit(request(accountId, "5.00"));

        assertThat(service.transactionHistory(accountId))
                .extracting(entry -> entry.entryType() + " " + entry.amount().toPlainString())
                .containsExactly("DEPOSIT 100.00", "WITHDRAWAL 40.00", "DEPOSIT 5.00");
    }

    @Test
    void collidingGeneratedAccountIdIsAServerFault() {
        LedgerStorage storage = mock(LedgerStorage.class);
        given(storage.addAccount(anyString())).willThrow(new DuplicateAccountException("collided"));

        // The caller never chose the id, so this must not surface as a 4xx. Being
        // an IllegalArgumentException subclass, an unwrapped one would map to 400.
        assertThatThrownBy(() -> new LedgerService(storage).createAccount())
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Internal storage error");
    }

    @Test
    void subCentAmountIsRejected() {
        String accountId = service.createAccount();

        assertThatThrownBy(() -> service.deposit(request(accountId, "0.001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finer than a cent");
    }

    @Test
    void absurdlyLargeAmountIsRejected() {
        String accountId = service.createAccount();

        // BigDecimal is arbitrary-precision: unbounded, this produced a
        // 1001-digit balance.
        assertThatThrownBy(() -> service.deposit(request(accountId, "1e1000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too large");

        assertThat(service.balance(accountId)).isEqualByComparingTo("0");
    }

    @Test
    void overlongDescriptionIsRejected() {
        String accountId = service.createAccount();
        EntryRequest tooLong = new EntryRequest(
                null, "d".repeat(257), accountId, new BigDecimal("10.00"));

        assertThatThrownBy(() -> service.deposit(tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description is too long");
    }

    @Test
    void overlongIdempotencyKeyIsRejected() {
        String accountId = service.createAccount();

        assertThatThrownBy(() -> service.deposit(request(accountId, "10.00", "k".repeat(65))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotency key is too long");
    }

    @Test
    void amountAtTheBoundariesIsAccepted() {
        String accountId = service.createAccount();

        service.deposit(request(accountId, "0.01"));
        service.deposit(request(accountId, "999999999999999.99"));

        assertThat(service.balance(accountId)).isEqualByComparingTo("1000000000000000.00");
    }

    @Test
    void balanceOfUnknownAccountIsRejected() {
        // Not a zero balance: the account does not exist.
        assertThatThrownBy(() -> service.balance("no-such-account"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void historyOfANewAccountIsEmpty() {
        assertThat(service.transactionHistory(service.createAccount())).isEmpty();
    }

    @Test
    void historyOfUnknownAccountIsRejected() {
        // Distinguishable from an account that exists but has no entries.
        assertThatThrownBy(() -> service.transactionHistory("no-such-account"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void historyIsNotPollutedByOtherAccounts() {
        String first = service.createAccount();
        String second = service.createAccount();

        service.deposit(request(first, "10.00"));
        service.deposit(request(second, "20.00"));

        assertThat(service.transactionHistory(first)).hasSize(1);
        assertThat(service.transactionHistory(first).getFirst().accountId()).isEqualTo(first);
    }

    @Test
    void rejectedEntryLeavesNoTraceInHistory() {
        String accountId = service.createAccount();
        service.deposit(request(accountId, "10.00"));

        assertThatThrownBy(() -> service.withdraw(request(accountId, "999.00")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(service.transactionHistory(accountId)).hasSize(1);
    }

    @Test
    void concurrentRetriesOfOneKeyApplyExactlyOnce() throws Exception {
        String accountId = service.createAccount();

        int threads = 50;
        Set<String> transactionIds = ConcurrentHashMap.newKeySet();
        AtomicInteger succeeded = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(16);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    transactionIds.add(service.deposit(request(accountId, "10.00", "key-3")).transactionId());
                    succeeded.incrementAndGet();
                } catch (Exception ignored) {
                    // failures show up as a short count or a wrong balance below
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        // Exactly one deposit was applied...
        assertThat(service.balance(accountId)).isEqualByComparingTo("10.00");
        // ...every caller got that same transaction back...
        assertThat(transactionIds).hasSize(1);
        // ...and nobody was turned away while it was being written.
        assertThat(succeeded.get()).isEqualTo(threads);
    }
}
