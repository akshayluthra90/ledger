package com.akshayluthra.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akshayluthra.ledger.dao.InMemoryLocalStorage;
import com.akshayluthra.ledger.exception.AccountNotFoundException;
import com.akshayluthra.ledger.exception.InsufficientFundsException;
import com.akshayluthra.ledger.model.EntryRequest;
import com.akshayluthra.ledger.model.LedgerEntry;

import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Test class for {@link LedgerService}
 */
class LedgerServiceTest {

    private final LedgerService service = new LedgerService(new InMemoryLocalStorage());

    private static EntryRequest request(String accountId, String amount, String idempotencyKey) {
        return new EntryRequest(idempotencyKey, "test entry", accountId, new BigDecimal(amount));
    }

    @Test
    void balanceReflectsDepositsAndWithdrawals() {
        String accountId = service.createAccount();

        service.deposit(request(accountId, "1000.00", null));
        service.withdraw(request(accountId, "650.00", null));

        assertThat(service.balance(accountId)).isEqualByComparingTo("350.00");
    }

    @Test
    void withdrawalBeyondBalanceIsRejected() {
        String accountId = service.createAccount();
        service.deposit(request(accountId, "100.00", null));

        assertThatThrownBy(() -> service.withdraw(request(accountId, "500.00", null)))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(service.balance(accountId)).isEqualByComparingTo("100.00");
    }

    @Test
    void depositToUnknownAccountIsRejected() {
        assertThatThrownBy(() -> service.deposit(request("no-such-account", "10.00", null)))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void repeatedKeyReplaysTheOriginalAndAppliesOnce() {
        String accountId = service.createAccount();

        LedgerEntry first = service.deposit(request(accountId, "100.00", "key-1"));
        LedgerEntry replay = service.deposit(request(accountId, "100.00", "key-1"));

        assertThat(replay.transactionId()).isEqualTo(first.transactionId());
        assertThat(service.balance(accountId)).isEqualByComparingTo("100.00");
    }

    @Test
    void sameKeyOnDifferentAccountsDoesNotCollide() {
        String first = service.createAccount();
        String second = service.createAccount();

        service.deposit(request(first, "10.00", "shared-key"));
        service.deposit(request(second, "25.00", "shared-key"));

        assertThat(service.balance(first)).isEqualByComparingTo("10.00");
        assertThat(service.balance(second)).isEqualByComparingTo("25.00");
    }

    @Test
    void rejectedRequestDoesNotBurnItsKey() {
        String accountId = service.createAccount();
        service.deposit(request(accountId, "50.00", null));

        assertThatThrownBy(() -> service.withdraw(request(accountId, "999.00", "key-2")))
                .isInstanceOf(InsufficientFundsException.class);

        // The same key must still be usable, otherwise every later retry would
        // be answered with a transaction that was never recorded.
        LedgerEntry retried = service.withdraw(request(accountId, "20.00", "key-2"));

        assertThat(retried).isNotNull();
        assertThat(service.balance(accountId)).isEqualByComparingTo("30.00");
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
