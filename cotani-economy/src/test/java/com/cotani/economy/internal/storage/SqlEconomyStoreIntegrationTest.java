package com.cotani.economy.internal.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cotani.economy.EconomySettings;
import com.cotani.economy.currency.EconomyCurrency;
import com.cotani.economy.exception.DuplicateEconomyOperationException;
import com.cotani.economy.exception.InsufficientFundsException;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.backend.SQLiteBackend;
import com.cotani.storage.backend.SQLiteCredentials;
import com.cotani.task.api.PaperTaskScheduler;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqlEconomyStoreIntegrationTest {

    private static final EconomySettings SETTINGS = EconomySettings.defaultSettings(EconomyCurrency.coins());
    private static final EconomyReason REASON = EconomyReason.system("integration-test");

    @TempDir
    Path tempDir;

    private @Nullable CotaniStorage storage;

    @AfterEach
    void closeStorage() {
        var current = storage;
        if (current != null) {
            current.closeAsync().toCompletableFuture().join();
        }
    }

    @Test
    void operationIdIsPersistentAndBoundToTheOriginalRequest() {
        var store = newStore();
        var userId = UUID.randomUUID();
        var operationId = EconomyOperationId.random();

        var first = store.deposit(userId, SETTINGS.defaultCurrency().id(), BigDecimal.TEN, REASON, operationId)
                .toCompletableFuture()
                .join();
        var exactRetry = store.deposit(userId, SETTINGS.defaultCurrency().id(), BigDecimal.TEN, REASON, operationId)
                .toCompletableFuture()
                .join();

        assertEquals(first.id(), exactRetry.id());
        assertFailure(
                DuplicateEconomyOperationException.class,
                () -> store.deposit(userId, SETTINGS.defaultCurrency().id(), BigDecimal.ONE, REASON, operationId)
                        .toCompletableFuture()
                        .join());
        assertFailure(
                DuplicateEconomyOperationException.class,
                () -> store.withdraw(userId, SETTINGS.defaultCurrency().id(), BigDecimal.ONE, REASON, operationId)
                        .toCompletableFuture()
                        .join());
    }

    @Test
    void concurrentWithdrawalsCannotSpendTheSameBalanceTwice() {
        var store = newStore();
        var userId = UUID.randomUUID();
        store.deposit(userId, SETTINGS.defaultCurrency().id(), BigDecimal.TEN, REASON, EconomyOperationId.random())
                .toCompletableFuture()
                .join();

        var first = store.withdraw(
                userId, SETTINGS.defaultCurrency().id(), BigDecimal.valueOf(7), REASON, EconomyOperationId.random());
        var second = store.withdraw(
                userId, SETTINGS.defaultCurrency().id(), BigDecimal.valueOf(7), REASON, EconomyOperationId.random());

        int successes = 0;
        int insufficient = 0;
        for (var operation : List.of(first, second)) {
            try {
                operation.toCompletableFuture().join();
                successes++;
            } catch (CompletionException failure) {
                assertInstanceOf(InsufficientFundsException.class, unwrap(failure));
                insufficient++;
            }
        }

        assertEquals(1, successes);
        assertEquals(1, insufficient);
        var account = store.getOrCreate(userId, SETTINGS.defaultCurrency().id())
                .toCompletableFuture()
                .join();
        assertEquals(0, account.balance().compareTo(new BigDecimal("3.00")));
    }

    private SqlEconomyStore newStore() {
        Plugin plugin = mock(Plugin.class);
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        Executor direct = Runnable::run;
        when(scheduler.asyncExecutor()).thenReturn(direct);
        var created = CotaniStorage.create(plugin)
                .backend(new SQLiteBackend(new SQLiteCredentials(tempDir.resolve("economy.db"))))
                .scheduler(scheduler)
                .migrations(new CreateEconomyTablesMigration())
                .build();
        created.startAsync().toCompletableFuture().join();
        storage = created;
        return new SqlEconomyStore(created, Clock.systemUTC(), SETTINGS);
    }

    private static <T extends Throwable> void assertFailure(Class<T> type, Runnable operation) {
        var failure = assertThrows(CompletionException.class, operation::run);
        assertInstanceOf(type, unwrap(failure));
    }

    private static Throwable unwrap(Throwable failure) {
        var current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
