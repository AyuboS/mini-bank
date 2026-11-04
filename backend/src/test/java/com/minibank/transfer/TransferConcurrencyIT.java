package com.minibank.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.minibank.account.domain.BankAccount;
import com.minibank.common.error.ApiException;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.money.CurrencyCode;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.ledger.service.LedgerService;
import com.minibank.support.AbstractIntegrationTest;
import com.minibank.transfer.dto.TransferRequest;
import com.minibank.transfer.service.TransferService;
import com.minibank.user.domain.User;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Proof that concurrent transfers cannot spend the same money twice.
 *
 * <p>These tests drive {@link TransferService} directly from several threads rather than going
 * through MockMvc. The behaviour under test is database row locking, and calling the service is the
 * shortest path to genuine parallel transactions — each thread gets its own connection, its own
 * transaction, and races the others for the same rows exactly as two HTTP requests would.
 *
 * <p>A {@link CountDownLatch} releases every thread at once, so the requests overlap instead of
 * politely queueing, which is what makes the race real rather than theoretical.
 */
@DisplayName("Concurrency: double spending")
class TransferConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private LedgerService ledgerService;

    private User alice;
    private User bob;
    private BankAccount aliceAccount;
    private BankAccount bobAccount;
    private AuthenticatedUser alicePrincipal;
    private AuthenticatedUser bobPrincipal;

    @BeforeEach
    void openAccounts() {
        alice = data.customer("alice");
        bob = data.customer("bob");
        aliceAccount = data.fundedAccount(alice, "100.00");
        bobAccount = data.fundedAccount(bob, "100.00");
        alicePrincipal = AuthenticatedUser.from(alice);
        bobPrincipal = AuthenticatedUser.from(bob);
    }

    private TransferRequest request(BankAccount from, BankAccount to, String amount) {
        // Category left null: this suite is about locking, and letting the server derive one keeps
        // the request identical to what a client would actually send.
        return new TransferRequest(
                from.getId(), to.getAccountNumber(), new BigDecimal(amount), CurrencyCode.EUR, "Concurrent", null);
    }

    /** Runs every task simultaneously and returns the outcomes, successes and failures alike. */
    private List<Outcome> runInParallel(List<Callable<Void>> tasks) throws Exception {
        int threads = tasks.size();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(threads);
        List<Outcome> outcomes = java.util.Collections.synchronizedList(new ArrayList<>());

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    startGate.await();
                    try {
                        task.call();
                        outcomes.add(new Outcome(true, null));
                    } catch (ApiException ex) {
                        outcomes.add(new Outcome(false, ex.getErrorCode()));
                    } catch (Exception ex) {
                        outcomes.add(new Outcome(false, null));
                    }
                    return null;
                }));
            }

            assertThat(ready.await(20, TimeUnit.SECONDS)).as("all threads reached the start gate").isTrue();
            startGate.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        }
        return outcomes;
    }

    @Test
    @Timeout(60)
    @DisplayName("100 EUR, two simultaneous transfers of 80: exactly one succeeds")
    void twoConcurrentTransfersCannotBothSpendTheSameBalance() throws Exception {
        List<Outcome> outcomes = runInParallel(List.of(
                () -> {
                    transferService.create(
                            request(aliceAccount, bobAccount, "80.00"), aliceP(), "key-" + UUID.randomUUID());
                    return null;
                },
                () -> {
                    transferService.create(
                            request(aliceAccount, bobAccount, "80.00"), aliceP(), "key-" + UUID.randomUUID());
                    return null;
                }));

        long succeeded = outcomes.stream().filter(Outcome::success).count();
        assertThat(succeeded).as("exactly one of the two transfers may settle").isEqualTo(1);
        assertThat(outcomes.stream().filter(o -> !o.success()).map(Outcome::errorCode))
                .containsExactly(ErrorCode.INSUFFICIENT_FUNDS);

        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber()))
                .as("the balance reflects one transfer, not two and not none")
                .isEqualByComparingTo("20.00");
        assertThat(databaseCleaner.balanceOf(bobAccount.getAccountNumber())).isEqualByComparingTo("180.00");
    }

    @Test
    @Timeout(90)
    @DisplayName("100 EUR, twenty simultaneous transfers of 10: exactly ten succeed")
    void manyConcurrentTransfersSettleExactlyAsManyTimesAsTheBalanceAllows() throws Exception {
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> {
                transferService.create(
                        request(aliceAccount, bobAccount, "10.00"), aliceP(), "key-" + UUID.randomUUID());
                return null;
            });
        }

        List<Outcome> outcomes = runInParallel(tasks);

        long succeeded = outcomes.stream().filter(Outcome::success).count();
        assertThat(succeeded).isEqualTo(10);
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("0");
        assertThat(databaseCleaner.balanceOf(bobAccount.getAccountNumber())).isEqualByComparingTo("200.00");
    }

    @Test
    @Timeout(60)
    @DisplayName("Opposing transfers A->B and B->A do not deadlock")
    void transfersInOppositeDirectionsDoNotDeadlock() throws Exception {
        // Without a deterministic lock order this is the textbook deadlock: one transaction holds A
        // and wants B while the other holds B and wants A. Locking in ascending account-id order
        // removes the cycle, so both complete instead of one dying on a deadlock detection.
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> {
                transferService.create(
                        request(aliceAccount, bobAccount, "5.00"), aliceP(), "key-" + UUID.randomUUID());
                return null;
            });
            tasks.add(() -> {
                transferService.create(
                        request(bobAccount, aliceAccount, "5.00"), bobP(), "key-" + UUID.randomUUID());
                return null;
            });
        }

        List<Outcome> outcomes = runInParallel(tasks);

        assertThat(outcomes.stream().filter(Outcome::success).count())
                .as("no transfer was lost to a deadlock")
                .isEqualTo(10);
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("100.00");
        assertThat(databaseCleaner.balanceOf(bobAccount.getAccountNumber())).isEqualByComparingTo("100.00");
    }

    @Test
    @Timeout(60)
    @DisplayName("The ledger stays consistent under contention")
    void theLedgerAndTheCachedBalanceAgreeAfterAContentiousRun() throws Exception {
        List<Callable<Void>> tasks = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger();
        for (int i = 0; i < 12; i++) {
            boolean outgoing = i % 2 == 0;
            tasks.add(() -> {
                int n = counter.incrementAndGet();
                if (outgoing) {
                    transferService.create(
                            request(aliceAccount, bobAccount, "7.00"), aliceP(), "key-" + n + "-" + UUID.randomUUID());
                } else {
                    transferService.create(
                            request(bobAccount, aliceAccount, "3.00"), bobP(), "key-" + n + "-" + UUID.randomUUID());
                }
                return null;
            });
        }

        runInParallel(tasks);

        assertThat(ledgerService.computeBalanceFromLedger(aliceAccount.getId()))
                .isEqualByComparingTo(databaseCleaner.balanceOf(aliceAccount.getAccountNumber()));
        assertThat(ledgerService.computeBalanceFromLedger(bobAccount.getId()))
                .isEqualByComparingTo(databaseCleaner.balanceOf(bobAccount.getAccountNumber()));
        assertThat(databaseCleaner
                        .balanceOf(aliceAccount.getAccountNumber())
                        .add(databaseCleaner.balanceOf(bobAccount.getAccountNumber())))
                .as("no money was created or destroyed")
                .isEqualByComparingTo("200.00");
    }

    private AuthenticatedUser aliceP() {
        return alicePrincipal;
    }

    private AuthenticatedUser bobP() {
        return bobPrincipal;
    }

    /** What one parallel attempt ended up doing. */
    private record Outcome(boolean success, ErrorCode errorCode) {}
}
