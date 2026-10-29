package com.akshayluthra.ledger.dao;

import com.akshayluthra.ledger.exception.AccountNotFoundException;
import com.akshayluthra.ledger.exception.DuplicateAccountException;
import com.akshayluthra.ledger.exception.DuplicateTransactionException;
import com.akshayluthra.ledger.exception.InsufficientFundsException;
import com.akshayluthra.ledger.exception.NoRecordFoundException;
import com.akshayluthra.ledger.model.EntryType;
import com.akshayluthra.ledger.model.LedgerEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test class for {@link InMemoryLocalStorage}
 */
public class InMemoryLocalStorageTest {

    private static final String TEST_ACCOUNT_ID = "test_account_id";
    private static final String TEST_ACCOUNT_ID_2 = "test_account_id_2";
    private static final String TEST_TXN_ID = "test_txn_id";
    private static final String TEST_TXN_ID_2 = "test_txn_id_2";

    InMemoryLocalStorage localStorage = new InMemoryLocalStorage();

    @BeforeEach
    public void init() {
        localStorage.addAccount(TEST_ACCOUNT_ID);
    }

    private static LedgerEntry entry(String accountId, String transactionId, EntryType entryType, String amount) {
        return new LedgerEntry(accountId, transactionId, entryType,
                "test entry", new BigDecimal(amount), Instant.EPOCH);
    }

    private static LedgerEntry deposit(String amount) {
        return entry(TEST_ACCOUNT_ID, UUID.randomUUID().toString(), EntryType.DEPOSIT, amount);
    }

    private static LedgerEntry withdrawal(String amount) {
        return entry(TEST_ACCOUNT_ID, UUID.randomUUID().toString(), EntryType.WITHDRAWAL, amount);
    }

    @Test
    void addRecord() {
        LedgerEntry ledgerEntry = entry(TEST_ACCOUNT_ID, TEST_TXN_ID, EntryType.DEPOSIT, "100.00");

        String transactionId = localStorage.addRecord(ledgerEntry);

        assertThat(transactionId).isEqualTo(TEST_TXN_ID);
    }

    @Test
    void addRecordWithNegativeAmountThrowsException() {
        LedgerEntry ledgerEntry = entry(TEST_ACCOUNT_ID, TEST_TXN_ID, EntryType.DEPOSIT, "-100.00");

        assertThatThrownBy(() -> localStorage.addRecord(ledgerEntry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount must be positive");
    }

    @Test
    void addRecordWithInvalidAccountThrowsException() {
        LedgerEntry ledgerEntry = entry(TEST_ACCOUNT_ID_2, TEST_TXN_ID, EntryType.DEPOSIT, "100.00");

        assertThatThrownBy(() -> localStorage.addRecord(ledgerEntry))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void getBalanceReturnsBalance() {
        BigDecimal balance = new BigDecimal("100.00");
        LedgerEntry ledgerEntry = entry(TEST_ACCOUNT_ID, TEST_TXN_ID, EntryType.DEPOSIT, "100.00");
        localStorage.addRecord(ledgerEntry);

        BigDecimal fetchedBalance = localStorage.getBalance(TEST_ACCOUNT_ID);

        assertThat(fetchedBalance).isEqualTo(balance);
    }

    // ---------- amount validation ----------

    @Test
    void addRecordWithZeroAmountThrowsException() {
        LedgerEntry ledgerEntry = entry(TEST_ACCOUNT_ID, TEST_TXN_ID, EntryType.DEPOSIT, "0.00");

        assertThatThrownBy(() -> localStorage.addRecord(ledgerEntry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount must be positive");
    }

    @Test
    void addRecordWithNullAmountThrowsException() {
        LedgerEntry ledgerEntry = new LedgerEntry(TEST_ACCOUNT_ID, TEST_TXN_ID, EntryType.DEPOSIT,
                "test entry", null, Instant.EPOCH);

        assertThatThrownBy(() -> localStorage.addRecord(ledgerEntry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount must be positive");
    }

    @Test
    void rejectedRecordLeavesNoTrace() {
        LedgerEntry rejected = entry(TEST_ACCOUNT_ID, TEST_TXN_ID, EntryType.DEPOSIT, "-5.00");

        assertThatThrownBy(() -> localStorage.addRecord(rejected))
                .isInstanceOf(IllegalArgumentException.class);

        // Every rejection happens before anything is written.
        assertThat(localStorage.getTransaction(TEST_TXN_ID)).isNull();
        assertThat(localStorage.getBalance(TEST_ACCOUNT_ID)).isEqualByComparingTo("0");
        assertThat(localStorage.getRecordsForAccount(TEST_ACCOUNT_ID)).isEmpty();
    }

    // ---------- withdrawals ----------

    @Test
    void withdrawalReducesBalance() {
        localStorage.addRecord(deposit("100.00"));
        localStorage.addRecord(withdrawal("40.00"));

        assertThat(localStorage.getBalance(TEST_ACCOUNT_ID)).isEqualByComparingTo("60.00");
    }

    @Test
    void withdrawalOfTheEntireBalanceIsAllowed() {
        localStorage.addRecord(deposit("100.00"));
        localStorage.addRecord(withdrawal("100.00"));

        assertThat(localStorage.getBalance(TEST_ACCOUNT_ID)).isEqualByComparingTo("0");
    }

    @Test
    void withdrawalBeyondBalanceThrowsAndLeavesBalanceUntouched() {
        localStorage.addRecord(deposit("100.00"));
        LedgerEntry tooMuch = entry(TEST_ACCOUNT_ID, TEST_TXN_ID_2, EntryType.WITHDRAWAL, "100.01");

        assertThatThrownBy(() -> localStorage.addRecord(tooMuch))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(localStorage.getBalance(TEST_ACCOUNT_ID)).isEqualByComparingTo("100.00");
        assertThat(localStorage.getTransaction(TEST_TXN_ID_2)).isNull();
    }

    @Test
    void withdrawalFromAnEmptyAccountThrowsException() {
        assertThatThrownBy(() -> localStorage.addRecord(withdrawal("0.01")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    // ---------- duplicate transaction ids ----------

    @Test
    void duplicateTransactionIdThrowsException() {
        localStorage.addRecord(entry(TEST_ACCOUNT_ID, TEST_TXN_ID, EntryType.DEPOSIT, "100.00"));
        LedgerEntry sameId = entry(TEST_ACCOUNT_ID, TEST_TXN_ID, EntryType.DEPOSIT, "50.00");

        assertThatThrownBy(() -> localStorage.addRecord(sameId))
                .isInstanceOf(DuplicateTransactionException.class);
    }

    @Test
    void duplicateTransactionIdDoesNotMoveTheBalance() {
        localStorage.addRecord(entry(TEST_ACCOUNT_ID, TEST_TXN_ID, EntryType.DEPOSIT, "100.00"));

        assertThatThrownBy(() -> localStorage.addRecord(
                entry(TEST_ACCOUNT_ID, TEST_TXN_ID, EntryType.DEPOSIT, "50.00")))
                .isInstanceOf(DuplicateTransactionException.class);

        // The duplicate check sits before the balance update inside the lock.
        assertThat(localStorage.getBalance(TEST_ACCOUNT_ID)).isEqualByComparingTo("100.00");
        assertThat(localStorage.getRecordsForAccount(TEST_ACCOUNT_ID)).hasSize(1);
    }

    @Test
    void transactionIdsAreGlobalNotPerAccount() {
        localStorage.addAccount(TEST_ACCOUNT_ID_2);
        localStorage.addRecord(entry(TEST_ACCOUNT_ID, TEST_TXN_ID, EntryType.DEPOSIT, "10.00"));

        assertThatThrownBy(() -> localStorage.addRecord(
                entry(TEST_ACCOUNT_ID_2, TEST_TXN_ID, EntryType.DEPOSIT, "10.00")))
                .isInstanceOf(DuplicateTransactionException.class);
    }

    // ---------- accounts ----------

    @Test
    void addAccountReturnsTheAccountId() {
        assertThat(localStorage.addAccount(TEST_ACCOUNT_ID_2)).isEqualTo(TEST_ACCOUNT_ID_2);
    }

    @Test
    void addAccountTwiceThrowsException() {
        assertThatThrownBy(() -> localStorage.addAccount(TEST_ACCOUNT_ID))
                .isInstanceOf(DuplicateAccountException.class);
    }

    @Test
    void newAccountStartsAtZero() {
        assertThat(localStorage.getBalance(TEST_ACCOUNT_ID)).isEqualByComparingTo("0");
    }

    @Test
    void getBalanceOfUnknownAccountThrowsException() {
        assertThatThrownBy(() -> localStorage.getBalance(TEST_ACCOUNT_ID_2))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void balancesAreIsolatedPerAccount() {
        localStorage.addAccount(TEST_ACCOUNT_ID_2);
        localStorage.addRecord(deposit("10.00"));
        localStorage.addRecord(entry(TEST_ACCOUNT_ID_2, TEST_TXN_ID_2, EntryType.DEPOSIT, "25.00"));

        assertThat(localStorage.getBalance(TEST_ACCOUNT_ID)).isEqualByComparingTo("10.00");
        assertThat(localStorage.getBalance(TEST_ACCOUNT_ID_2)).isEqualByComparingTo("25.00");
    }

    @Test
    void deleteAccountRemovesIt() {
        localStorage.deleteAccount(TEST_ACCOUNT_ID);

        assertThatThrownBy(() -> localStorage.getBalance(TEST_ACCOUNT_ID))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void deleteUnknownAccountThrowsException() {
        assertThatThrownBy(() -> localStorage.deleteAccount(TEST_ACCOUNT_ID_2))
                .isInstanceOf(NoRecordFoundException.class);
    }

    @Test
    void deleteAccountTwiceThrowsOnTheSecondCall() {
        localStorage.deleteAccount(TEST_ACCOUNT_ID);

        assertThatThrownBy(() -> localStorage.deleteAccount(TEST_ACCOUNT_ID))
                .isInstanceOf(NoRecordFoundException.class);
    }

    @Test
    void deletingAnAccountDoesNotEraseItsTransactions() {
        localStorage.addRecord(entry(TEST_ACCOUNT_ID, TEST_TXN_ID, EntryType.DEPOSIT, "100.00"));

        localStorage.deleteAccount(TEST_ACCOUNT_ID);

        // Financial history outlives the account; the entry is still reachable by
        // id even though the account index is gone.
        assertThat(localStorage.getTransaction(TEST_TXN_ID)).isNotNull();
    }

    @Test
    void recordingAgainstADeletedAccountThrows() {
        localStorage.deleteAccount(TEST_ACCOUNT_ID);

        assertThatThrownBy(() -> localStorage.addRecord(deposit("10.00")))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ---------- transaction lookup ----------

    @Test
    void getTransactionReturnsTheStoredEntry() {
        LedgerEntry stored = entry(TEST_ACCOUNT_ID, TEST_TXN_ID, EntryType.DEPOSIT, "100.00");
        localStorage.addRecord(stored);

        assertThat(localStorage.getTransaction(TEST_TXN_ID)).isEqualTo(stored);
    }

    @Test
    void getTransactionReturnsNullForUnknownId() {
        // Null rather than an exception: the service turns this into the 404.
        assertThat(localStorage.getTransaction("invalid_transaction_id")).isNull();
    }

    // ---------- history ----------

    @Test
    void getRecordsForAccountIsEmptyForANewAccount() {
        assertThat(localStorage.getRecordsForAccount(TEST_ACCOUNT_ID)).isEmpty();
    }

    @Test
    void getRecordsForUnknownAccountThrowsException() {
        assertThatThrownBy(() -> localStorage.getRecordsForAccount(TEST_ACCOUNT_ID_2))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void getRecordsForAccountIsOldestFirst() {
        localStorage.addRecord(deposit("100.00"));
        localStorage.addRecord(withdrawal("40.00"));
        localStorage.addRecord(deposit("5.00"));

        assertThat(localStorage.getRecordsForAccount(TEST_ACCOUNT_ID))
                .extracting(e -> e.entryType() + " " + e.amount().toPlainString())
                .containsExactly("DEPOSIT 100.00", "WITHDRAWAL 40.00", "DEPOSIT 5.00");
    }

    @Test
    void getRecordsForAccountExcludesOtherAccounts() {
        localStorage.addAccount(TEST_ACCOUNT_ID_2);
        localStorage.addRecord(deposit("10.00"));
        localStorage.addRecord(entry(TEST_ACCOUNT_ID_2, TEST_TXN_ID_2, EntryType.DEPOSIT, "25.00"));

        assertThat(localStorage.getRecordsForAccount(TEST_ACCOUNT_ID))
                .singleElement()
                .extracting(LedgerEntry::accountId)
                .isEqualTo(TEST_ACCOUNT_ID);
    }

    @Test
    void getRecordsForAccountIsImmutable() {
        localStorage.addRecord(deposit("10.00"));

        assertThatThrownBy(() -> localStorage.getRecordsForAccount(TEST_ACCOUNT_ID).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ---------- concurrency ----------

    @Test
    void concurrentWithdrawalsNeverOverdrawTheAccount() throws Exception {
        localStorage.addRecord(deposit("10.00"));

        int threads = 50;
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(16);

        // 50 threads each take 1.00 from a balance of 10.00. The monitor holds the
        // overdraft check and the balance update together, so exactly ten can win.
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    localStorage.addRecord(withdrawal("1.00"));
                    succeeded.incrementAndGet();
                } catch (InsufficientFundsException ex) {
                    refused.incrementAndGet();
                } catch (Exception ignored) {
                    // anything else shows up as a short count below
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(succeeded.get()).isEqualTo(10);
        assertThat(refused.get()).isEqualTo(threads - 10);
        assertThat(localStorage.getBalance(TEST_ACCOUNT_ID)).isEqualByComparingTo("0");
        assertThat(localStorage.getRecordsForAccount(TEST_ACCOUNT_ID)).hasSize(11);
    }

    @Test
    void concurrentDepositsAllLand() throws Exception {
        int threads = 100;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(16);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    localStorage.addRecord(deposit("1.00"));
                } catch (Exception ignored) {
                    // a lost deposit shows up as a wrong balance below
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        // No lost updates: read-modify-write of the balance is inside the monitor.
        assertThat(localStorage.getBalance(TEST_ACCOUNT_ID)).isEqualByComparingTo("100.00");
        assertThat(localStorage.getRecordsForAccount(TEST_ACCOUNT_ID)).hasSize(threads);
    }
}
