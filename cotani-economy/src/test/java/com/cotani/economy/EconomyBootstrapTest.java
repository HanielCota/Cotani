package com.cotani.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.currency.EconomyCurrency;
import com.cotani.economy.event.EconomyEventPublisher;
import com.cotani.economy.event.EconomyTransactionEvent;
import com.cotani.economy.exception.DuplicateEconomyOperationException;
import com.cotani.economy.exception.InsufficientFundsException;
import com.cotani.economy.exception.MaximumBalanceExceededException;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EconomyBootstrapTest {
    private static final CurrencyId CURRENCY = EconomyCurrency.coins().id();
    private static final EconomyReason REASON = EconomyReason.system("test");

    private final List<AutoCloseable> toClose = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (var closeable : toClose) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Best-effort cleanup.
            }
        }
    }

    private EconomyBootstrap newBootstrap() {
        var bootstrap = EconomyBootstrap.createDefault();
        toClose.add(bootstrap);
        return bootstrap;
    }

    private static <T extends Throwable> T assertCause(Class<T> type, CompletableFuture<?> future) {
        var failure = assertThrows(CompletionException.class, future::join);
        assertNotNull(failure.getCause());
        assertInstanceOf(type, failure.getCause());
        return type.cast(failure.getCause());
    }

    @Test
    void shouldDepositAndReportBalance() {
        var bootstrap = newBootstrap();
        var userId = UUID.randomUUID();

        var transaction = bootstrap
                .service()
                .deposit(userId, CURRENCY, BigDecimal.TEN, REASON, EconomyOperationId.random())
                .toCompletableFuture()
                .join();

        assertEquals(EconomyTransaction.Deposit.class, transaction.getClass());
        var balance = bootstrap.service().balance(userId).toCompletableFuture().join();
        assertEquals(0, balance.amount().compareTo(new BigDecimal("10.00")));
    }

    @Test
    void shouldWithdrawWhenFundsAreAvailable() {
        var bootstrap = newBootstrap();
        var userId = UUID.randomUUID();

        bootstrap
                .service()
                .deposit(userId, CURRENCY, BigDecimal.TEN, REASON, EconomyOperationId.random())
                .toCompletableFuture()
                .join();
        bootstrap
                .service()
                .withdraw(userId, CURRENCY, new BigDecimal("4.50"), REASON, EconomyOperationId.random())
                .toCompletableFuture()
                .join();

        var balance = bootstrap.service().balance(userId).toCompletableFuture().join();
        assertEquals(0, balance.amount().compareTo(new BigDecimal("5.50")));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldFailWithdrawalWhenFundsAreInsufficient() {
        var bootstrap = newBootstrap();
        var userId = UUID.randomUUID();

        var failure = assertCause(
                InsufficientFundsException.class,
                bootstrap
                        .service()
                        .withdraw(userId, CURRENCY, BigDecimal.TEN, REASON, EconomyOperationId.random())
                        .toCompletableFuture());

        assertTrue(failure.getMessage().contains(userId.toString()));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectDepositAboveMaximumBalance() {
        var settings = new EconomySettings(
                EconomyCurrency.coins(),
                BigDecimal.ZERO,
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                BigDecimal.ONE,
                30,
                60);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var bootstrap = EconomyBootstrap.create(settings, new RecordingPublisher(), executor);
        toClose.add(bootstrap);
        var userId = UUID.randomUUID();

        bootstrap
                .service()
                .deposit(userId, CURRENCY, new BigDecimal("100"), REASON, EconomyOperationId.random())
                .toCompletableFuture()
                .join();
        var failure = assertCause(
                MaximumBalanceExceededException.class,
                bootstrap
                        .service()
                        .deposit(userId, CURRENCY, BigDecimal.ONE, REASON, EconomyOperationId.random())
                        .toCompletableFuture());

        assertTrue(failure.getMessage().contains(userId.toString()));
    }

    @Test
    void shouldTransferBetweenUsers() {
        var bootstrap = newBootstrap();
        var source = UUID.randomUUID();
        var target = UUID.randomUUID();

        bootstrap
                .service()
                .deposit(source, CURRENCY, new BigDecimal("20"), REASON, EconomyOperationId.random())
                .toCompletableFuture()
                .join();
        bootstrap
                .service()
                .transfer(source, target, CURRENCY, new BigDecimal("7.25"), REASON, EconomyOperationId.random())
                .toCompletableFuture()
                .join();

        assertEquals(
                0,
                bootstrap
                        .service()
                        .balance(source)
                        .toCompletableFuture()
                        .join()
                        .amount()
                        .compareTo(new BigDecimal("12.75")));
        assertEquals(
                0,
                bootstrap
                        .service()
                        .balance(target)
                        .toCompletableFuture()
                        .join()
                        .amount()
                        .compareTo(new BigDecimal("7.25")));
    }

    @Test
    void shouldReplaySameOperationIdWithoutApplyingTwice() {
        var bootstrap = newBootstrap();
        var userId = UUID.randomUUID();
        var operationId = EconomyOperationId.random();

        var first = bootstrap
                .service()
                .deposit(userId, CURRENCY, BigDecimal.TEN, REASON, operationId)
                .toCompletableFuture()
                .join();
        var second = bootstrap
                .service()
                .deposit(userId, CURRENCY, BigDecimal.TEN, REASON, operationId)
                .toCompletableFuture()
                .join();

        assertEquals(first, second);
        assertEquals(
                0,
                bootstrap
                        .service()
                        .balance(userId)
                        .toCompletableFuture()
                        .join()
                        .amount()
                        .compareTo(BigDecimal.TEN));
    }

    @Test
    void shouldRejectReusedOperationIdWithDifferentRequest() {
        var bootstrap = newBootstrap();
        var userId = UUID.randomUUID();
        var operationId = EconomyOperationId.random();

        bootstrap
                .service()
                .deposit(userId, CURRENCY, BigDecimal.TEN, REASON, operationId)
                .toCompletableFuture()
                .join();

        assertCause(
                DuplicateEconomyOperationException.class,
                bootstrap
                        .service()
                        .deposit(userId, CURRENCY, new BigDecimal("20"), REASON, operationId)
                        .toCompletableFuture());
    }

    @Test
    void shouldPublishTransactionEvents() {
        var settings = EconomySettings.defaultSettings(EconomyCurrency.coins());
        var events = new ArrayList<EconomyTransactionEvent>();
        EconomyEventPublisher publisher = events::add;
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var bootstrap = EconomyBootstrap.create(settings, publisher, executor);
        toClose.add(bootstrap);
        var userId = UUID.randomUUID();

        bootstrap
                .service()
                .deposit(userId, CURRENCY, BigDecimal.TEN, REASON, EconomyOperationId.random())
                .toCompletableFuture()
                .join();

        assertEquals(1, events.size());
        assertEquals(userId, events.get(0).transaction().target().orElseThrow());
    }

    @Test
    void shouldSupportRepeatedClose() {
        var bootstrap = newBootstrap();

        bootstrap.close();
        bootstrap.close();
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullArgumentsToCustomFactory() {
        assertThrows(
                NullPointerException.class,
                () -> EconomyBootstrap.create(
                        null, new RecordingPublisher(), Executors.newVirtualThreadPerTaskExecutor()));
        assertThrows(
                NullPointerException.class,
                () -> EconomyBootstrap.create(
                        EconomySettings.defaultSettings(EconomyCurrency.coins()),
                        null,
                        Executors.newVirtualThreadPerTaskExecutor()));
        assertThrows(
                NullPointerException.class,
                () -> EconomyBootstrap.create(
                        EconomySettings.defaultSettings(EconomyCurrency.coins()), new RecordingPublisher(), null));
    }

    @Test
    void shouldStartAccountsAtConfiguredStartingBalance() {
        var settings = new EconomySettings(
                EconomyCurrency.coins(),
                new BigDecimal("50.00"),
                new BigDecimal("1000000000000.00"),
                new BigDecimal("100000000.00"),
                BigDecimal.ONE,
                30,
                60);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var bootstrap = EconomyBootstrap.create(settings, new RecordingPublisher(), executor);
        toClose.add(bootstrap);

        var balance = bootstrap
                .service()
                .balance(UUID.randomUUID())
                .toCompletableFuture()
                .join();

        assertEquals(0, balance.amount().compareTo(new BigDecimal("50.00")));
    }

    @Test
    void shouldDistinguishTransactionsByOperationId() {
        var bootstrap = newBootstrap();
        var userId = UUID.randomUUID();

        var first = bootstrap
                .service()
                .deposit(userId, CURRENCY, BigDecimal.TEN, REASON, EconomyOperationId.random())
                .toCompletableFuture()
                .join();
        var second = bootstrap
                .service()
                .deposit(userId, CURRENCY, new BigDecimal("5"), REASON, EconomyOperationId.random())
                .toCompletableFuture()
                .join();

        assertNotEquals(first, second);
        assertEquals(
                0,
                bootstrap
                        .service()
                        .balance(userId)
                        .toCompletableFuture()
                        .join()
                        .amount()
                        .compareTo(new BigDecimal("15.00")));
    }

    private static final class RecordingPublisher implements EconomyEventPublisher {
        @Override
        public void publish(EconomyTransactionEvent event) {}
    }
}
