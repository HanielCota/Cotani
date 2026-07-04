package com.cotani.economy.internal.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.economy.EconomySettings;
import com.cotani.economy.currency.EconomyCurrency;
import com.cotani.economy.exception.DuplicateEconomyOperationException;
import com.cotani.economy.exception.InsufficientFundsException;
import com.cotani.economy.exception.MaximumBalanceExceededException;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class InMemoryEconomyStoreTest {

    private static final EconomySettings SETTINGS = EconomySettings.defaultSettings(EconomyCurrency.coins());
    private static final EconomyReason REASON = EconomyReason.system("test");

    private static <T extends Throwable> T assertCause(Class<T> type, CompletableFuture<?> future) {
        var exception = assertThrows(CompletionException.class, future::join);
        assertNotNull(exception.getCause());
        assertInstanceOf(type, exception.getCause());
        return type.cast(exception.getCause());
    }

    private InMemoryEconomyStore newStore() {
        return new InMemoryEconomyStore(Executors.newVirtualThreadPerTaskExecutor(), Clock.systemUTC(), SETTINGS);
    }

    @Test
    void getOrCreateReturnsStartingBalanceForNewAccount() {
        var store = newStore();
        var userId = UUID.randomUUID();

        var account = store.getOrCreate(userId, SETTINGS.defaultCurrency().id())
                .toCompletableFuture()
                .join();

        assertEquals(0, account.balance().compareTo(SETTINGS.startingBalance()));
    }

    @Test
    void depositIncreasesBalance() {
        var store = newStore();
        var userId = UUID.randomUUID();
        var operationId = EconomyOperationId.random();

        var transaction = store.deposit(userId, SETTINGS.defaultCurrency().id(), BigDecimal.TEN, REASON, operationId)
                .toCompletableFuture()
                .join();

        assertEquals(0, transaction.amount().compareTo(BigDecimal.TEN));
        assertNotNull(transaction.targetBalanceAfter());
        assertEquals(0, transaction.targetBalanceAfter().compareTo(BigDecimal.TEN.add(SETTINGS.startingBalance())));
    }

    @Test
    void depositFailsForDuplicateOperationId() {
        var store = newStore();
        var userId = UUID.randomUUID();
        var operationId = EconomyOperationId.random();

        store.deposit(userId, SETTINGS.defaultCurrency().id(), BigDecimal.TEN, REASON, operationId)
                .toCompletableFuture()
                .join();

        assertCause(
                DuplicateEconomyOperationException.class,
                store.deposit(userId, SETTINGS.defaultCurrency().id(), BigDecimal.ONE, REASON, operationId)
                        .toCompletableFuture());
    }

    @Test
    void withdrawFailsWhenBalanceIsInsufficient() {
        var store = newStore();
        var userId = UUID.randomUUID();

        assertCause(
                InsufficientFundsException.class,
                store.withdraw(
                                userId,
                                SETTINGS.defaultCurrency().id(),
                                BigDecimal.ONE,
                                REASON,
                                EconomyOperationId.random())
                        .toCompletableFuture());
    }

    @Test
    void withdrawSucceedsAfterDeposit() {
        var store = newStore();
        var userId = UUID.randomUUID();
        store.deposit(userId, SETTINGS.defaultCurrency().id(), BigDecimal.TEN, REASON, EconomyOperationId.random())
                .toCompletableFuture()
                .join();

        var transaction = store.withdraw(
                        userId,
                        SETTINGS.defaultCurrency().id(),
                        BigDecimal.valueOf(3),
                        REASON,
                        EconomyOperationId.random())
                .toCompletableFuture()
                .join();

        assertEquals(0, transaction.amount().compareTo(BigDecimal.valueOf(3)));
    }

    @Test
    void transferUpdatesBothAccounts() {
        var store = newStore();
        var source = UUID.randomUUID();
        var target = UUID.randomUUID();
        store.deposit(source, SETTINGS.defaultCurrency().id(), BigDecimal.TEN, REASON, EconomyOperationId.random())
                .toCompletableFuture()
                .join();

        var transaction = store.transfer(
                        source,
                        target,
                        SETTINGS.defaultCurrency().id(),
                        BigDecimal.valueOf(4),
                        REASON,
                        EconomyOperationId.random())
                .toCompletableFuture()
                .join();

        assertNotNull(transaction.sourceBalanceAfter());
        assertNotNull(transaction.targetBalanceAfter());
        assertEquals(
                0,
                transaction.sourceBalanceAfter().compareTo(BigDecimal.valueOf(6).add(SETTINGS.startingBalance())));
        assertEquals(
                0,
                transaction.targetBalanceAfter().compareTo(BigDecimal.valueOf(4).add(SETTINGS.startingBalance())));
    }

    @Test
    void transferFailsWhenSourceBalanceIsInsufficient() {
        var store = newStore();
        var source = UUID.randomUUID();
        var target = UUID.randomUUID();

        assertCause(
                InsufficientFundsException.class,
                store.transfer(
                                source,
                                target,
                                SETTINGS.defaultCurrency().id(),
                                BigDecimal.ONE,
                                REASON,
                                EconomyOperationId.random())
                        .toCompletableFuture());
    }

    @Test
    void depositFailsWhenMaximumBalanceWouldBeExceeded() {
        var store = newStore();
        var userId = UUID.randomUUID();
        var amount = SETTINGS.maximumBalance().add(BigDecimal.ONE);

        assertCause(
                MaximumBalanceExceededException.class,
                store.deposit(userId, SETTINGS.defaultCurrency().id(), amount, REASON, EconomyOperationId.random())
                        .toCompletableFuture());
    }
}
