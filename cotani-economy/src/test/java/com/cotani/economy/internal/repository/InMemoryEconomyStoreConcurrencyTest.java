package com.cotani.economy.internal.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.economy.EconomySettings;
import com.cotani.economy.currency.EconomyCurrency;
import com.cotani.economy.exception.DuplicateEconomyOperationException;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class InMemoryEconomyStoreConcurrencyTest {

    private static final EconomySettings SETTINGS = EconomySettings.defaultSettings(EconomyCurrency.coins());
    private static final EconomyReason REASON = EconomyReason.system("test");

    private InMemoryEconomyStore newStore(ExecutorService executor) {
        return new InMemoryEconomyStore(executor, Clock.systemUTC(), SETTINGS);
    }

    @Test
    void concurrentDepositsProduceCorrectFinalBalance() throws InterruptedException {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var store = newStore(executor);
            var userId = UUID.randomUUID();
            int threads = 100;
            var latch = new CountDownLatch(threads);
            BigDecimal amount = BigDecimal.ONE;

            var futures = IntStream.range(0, threads)
                    .mapToObj(_ -> CompletableFuture.supplyAsync(
                            () -> {
                                latch.countDown();
                                await(latch);
                                return store.deposit(
                                                userId,
                                                SETTINGS.defaultCurrency().id(),
                                                amount,
                                                REASON,
                                                EconomyOperationId.random())
                                        .toCompletableFuture()
                                        .join();
                            },
                            executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                    .join();

            var account = store.getOrCreate(userId, SETTINGS.defaultCurrency().id())
                    .toCompletableFuture()
                    .join();
            var expected = SETTINGS.startingBalance().add(amount.multiply(BigDecimal.valueOf(threads)));
            assertEquals(0, account.balance().compareTo(expected));
        }
    }

    @Test
    void concurrentTransfersPreserveTotalBalance() throws InterruptedException {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var store = newStore(executor);
            var source = UUID.randomUUID();
            var target = UUID.randomUUID();
            store.deposit(
                            source,
                            SETTINGS.defaultCurrency().id(),
                            BigDecimal.valueOf(1000),
                            REASON,
                            EconomyOperationId.random())
                    .toCompletableFuture()
                    .join();

            int threads = 100;
            var latch = new CountDownLatch(threads);
            BigDecimal amount = BigDecimal.ONE;

            var futures = IntStream.range(0, threads)
                    .mapToObj(_ -> CompletableFuture.supplyAsync(
                            () -> {
                                latch.countDown();
                                await(latch);
                                return store.transfer(
                                                source,
                                                target,
                                                SETTINGS.defaultCurrency().id(),
                                                amount,
                                                REASON,
                                                EconomyOperationId.random())
                                        .toCompletableFuture()
                                        .join();
                            },
                            executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                    .join();

            var sourceAccount = store.getOrCreate(
                            source, SETTINGS.defaultCurrency().id())
                    .toCompletableFuture()
                    .join();
            var targetAccount = store.getOrCreate(
                            target, SETTINGS.defaultCurrency().id())
                    .toCompletableFuture()
                    .join();

            assertEquals(
                    0,
                    sourceAccount
                            .balance()
                            .compareTo(SETTINGS.startingBalance().add(BigDecimal.valueOf(1000 - threads))));
            assertEquals(
                    0,
                    targetAccount.balance().compareTo(SETTINGS.startingBalance().add(BigDecimal.valueOf(threads))));
        }
    }

    @Test
    void concurrentDuplicateOperationIdsAreRejectedWithoutDoubleSpending() throws InterruptedException {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var store = newStore(executor);
            var userId = UUID.randomUUID();
            var operationId = EconomyOperationId.random();
            int threads = 50;
            var latch = new CountDownLatch(threads);
            BigDecimal amount = BigDecimal.TEN;

            List<CompletableFuture<Throwable>> outcomes = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                outcomes.add(CompletableFuture.supplyAsync(
                        () -> {
                            latch.countDown();
                            await(latch);
                            try {
                                store.deposit(userId, SETTINGS.defaultCurrency().id(), amount, REASON, operationId)
                                        .toCompletableFuture()
                                        .join();
                                return null;
                            } catch (Exception exception) {
                                return exception.getCause() != null ? exception.getCause() : exception;
                            }
                        },
                        executor));
            }

            CompletableFuture.allOf(outcomes.toArray(new CompletableFuture<?>[0]))
                    .join();

            long successes = outcomes.stream().filter(f -> f.join() == null).count();
            long duplicates = outcomes.stream()
                    .filter(f -> f.join() instanceof DuplicateEconomyOperationException)
                    .count();

            assertEquals(1, successes, "exactly one operation with the same id should succeed");
            assertEquals(threads - 1, duplicates, "all others should be rejected as duplicates");

            var account = store.getOrCreate(userId, SETTINGS.defaultCurrency().id())
                    .toCompletableFuture()
                    .join();
            assertEquals(
                    0, account.balance().compareTo(SETTINGS.startingBalance().add(amount)));
        }
    }

    @Test
    void transferBetweenSameAccountDoesNotDeadlock() {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var store = newStore(executor);
            var userId = UUID.randomUUID();
            store.deposit(
                            userId,
                            SETTINGS.defaultCurrency().id(),
                            BigDecimal.valueOf(100),
                            REASON,
                            EconomyOperationId.random())
                    .toCompletableFuture()
                    .join();

            var transaction = store.transfer(
                            userId,
                            userId,
                            SETTINGS.defaultCurrency().id(),
                            BigDecimal.TEN,
                            REASON,
                            EconomyOperationId.random())
                    .toCompletableFuture()
                    .join();

            assertNotNull(transaction);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        }
    }
}
