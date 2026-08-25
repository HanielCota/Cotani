package com.cotani.economy.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cotani.economy.EconomySettings;
import com.cotani.economy.account.EconomyAccount;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.currency.EconomyCurrency;
import com.cotani.economy.event.EconomyEventPublisher;
import com.cotani.economy.exception.InvalidAmountException;
import com.cotani.economy.exception.SameEconomyAccountTransferException;
import com.cotani.economy.internal.protection.DefaultEconomyGuard;
import com.cotani.economy.internal.repository.EconomyAccountRepository;
import com.cotani.economy.internal.repository.EconomyTransferRepository;
import com.cotani.economy.transaction.EconomyBalanceChange;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import com.cotani.economy.transaction.EconomyTransactionDetails;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DefaultEconomyServiceBoundaryTest {
    private static final EconomySettings SETTINGS = EconomySettings.defaultSettings(EconomyCurrency.coins());
    private static final CurrencyId CURRENCY = SETTINGS.defaultCurrency().id();
    private static final EconomyReason REASON = EconomyReason.system("test");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();

    private final EconomyAccountRepository accountRepository = mock(EconomyAccountRepository.class);
    private final EconomyTransferRepository transferRepository = mock(EconomyTransferRepository.class);
    private final EconomyEventPublisher eventPublisher = mock(EconomyEventPublisher.class);

    private DefaultEconomyService newService() {
        when(eventPublisher.publishAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
        return DefaultEconomyService.create(
                SETTINGS, new DefaultEconomyGuard(SETTINGS), accountRepository, transferRepository, eventPublisher);
    }

    private static <T extends Throwable> T assertCause(Class<T> type, CompletableFuture<?> future) {
        var exception = assertThrows(CompletionException.class, future::join);
        assertTrue(type.isInstance(exception.getCause()));
        return type.cast(exception.getCause());
    }

    private static EconomyAccount account(BigDecimal balance) {
        return EconomyAccount.create(USER_ID, CURRENCY, balance, Instant.now());
    }

    @Test
    void shouldQueryBalanceForCurrencyOverload() {
        var service = newService();
        when(accountRepository.getOrCreate(USER_ID, CURRENCY))
                .thenReturn(CompletableFuture.completedFuture(account(BigDecimal.valueOf(7))));

        var balance = service.balance(USER_ID, CURRENCY).toCompletableFuture().join();

        assertEquals(0, balance.amount().compareTo(new BigDecimal("7.00")));
        verify(accountRepository).getOrCreate(USER_ID, CURRENCY);
    }

    @Test
    void shouldQueryBalanceForDefaultCurrencyOverload() {
        var service = newService();
        when(accountRepository.getOrCreate(USER_ID, CURRENCY))
                .thenReturn(CompletableFuture.completedFuture(account(BigDecimal.TEN)));

        var balance = service.balance(USER_ID).toCompletableFuture().join();

        assertEquals(0, balance.amount().compareTo(new BigDecimal("10.00")));
        verify(accountRepository).getOrCreate(USER_ID, CURRENCY);
    }

    @Test
    void shouldCheckFundsForCurrencyOverload() {
        var service = newService();
        when(accountRepository.getOrCreate(USER_ID, CURRENCY))
                .thenReturn(CompletableFuture.completedFuture(account(BigDecimal.TEN)));

        assertTrue(service.has(USER_ID, CURRENCY, BigDecimal.TEN)
                .toCompletableFuture()
                .join());
        assertFalse(service.has(USER_ID, CURRENCY, new BigDecimal("10.01"))
                .toCompletableFuture()
                .join());
    }

    @Test
    void shouldSetBalanceForCurrencyOverload() {
        var service = newService();
        var transaction = EconomyTransaction.set(
                new EconomyTransactionDetails(
                        EconomyOperationId.random(), CURRENCY, BigDecimal.TEN, REASON, Instant.now()),
                new EconomyBalanceChange(USER_ID, BigDecimal.ZERO, BigDecimal.TEN));
        var normalized = BigDecimal.TEN.setScale(2);
        when(accountRepository.set(eq(USER_ID), eq(CURRENCY), eq(normalized), eq(REASON), any()))
                .thenReturn(CompletableFuture.completedFuture(transaction));

        var result = service.set(USER_ID, CURRENCY, BigDecimal.TEN, REASON, EconomyOperationId.random())
                .toCompletableFuture()
                .join();

        assertEquals(transaction, result);
    }

    @Test
    void shouldTransferForCurrencyOverload() {
        var service = newService();
        var transaction = EconomyTransaction.transfer(
                new EconomyTransactionDetails(
                        EconomyOperationId.random(), CURRENCY, BigDecimal.TEN, REASON, Instant.now()),
                new EconomyBalanceChange(USER_ID, BigDecimal.TEN, BigDecimal.ZERO),
                new EconomyBalanceChange(OTHER_USER_ID, BigDecimal.ZERO, BigDecimal.TEN));
        var normalized = BigDecimal.TEN.setScale(2);
        when(transferRepository.transfer(
                        eq(USER_ID), eq(OTHER_USER_ID), eq(CURRENCY), eq(normalized), eq(REASON), any()))
                .thenReturn(CompletableFuture.completedFuture(transaction));

        var result = service.transfer(
                        USER_ID, OTHER_USER_ID, CURRENCY, BigDecimal.TEN, REASON, EconomyOperationId.random())
                .toCompletableFuture()
                .join();

        assertEquals(transaction, result);
    }

    @Test
    void shouldWithdrawForDefaultCurrencyOverload() {
        var service = newService();
        var transaction = EconomyTransaction.withdraw(
                new EconomyTransactionDetails(
                        EconomyOperationId.random(), CURRENCY, BigDecimal.TEN, REASON, Instant.now()),
                new EconomyBalanceChange(USER_ID, BigDecimal.TEN, BigDecimal.ZERO));
        var normalized = BigDecimal.TEN.setScale(2);
        when(accountRepository.withdraw(eq(USER_ID), eq(CURRENCY), eq(normalized), eq(REASON), any()))
                .thenReturn(CompletableFuture.completedFuture(transaction));

        var result = service.withdraw(USER_ID, BigDecimal.TEN, REASON, EconomyOperationId.random())
                .toCompletableFuture()
                .join();

        assertEquals(transaction, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "0.001"})
    void shouldRejectInvalidDepositAmountsSynchronously(String rawAmount) {
        var service = newService();

        assertCause(
                InvalidAmountException.class,
                service.deposit(USER_ID, CURRENCY, new BigDecimal(rawAmount), REASON, EconomyOperationId.random())
                        .toCompletableFuture());
        verifyNoInteractions(accountRepository);
    }

    @Test
    void shouldRejectDepositAboveMaximumOperationAmountSynchronously() {
        var service = newService();
        var tooLarge = SETTINGS.maximumOperationAmount().add(BigDecimal.ONE);

        assertCause(
                InvalidAmountException.class,
                service.deposit(USER_ID, CURRENCY, tooLarge, REASON, EconomyOperationId.random())
                        .toCompletableFuture());
    }

    @Test
    void shouldRejectSetBalanceAboveMaximumSynchronously() {
        var service = newService();
        var tooLarge = SETTINGS.maximumBalance().add(BigDecimal.ONE);

        assertCause(
                InvalidAmountException.class,
                service.set(USER_ID, CURRENCY, tooLarge, REASON, EconomyOperationId.random())
                        .toCompletableFuture());
    }

    @Test
    void shouldRejectUnknownCurrencySynchronously() {
        var service = newService();
        var unknown = CurrencyId.of("unknown");

        assertCause(
                IllegalArgumentException.class,
                service.balance(USER_ID, unknown).toCompletableFuture());
        assertCause(
                IllegalArgumentException.class,
                service.deposit(USER_ID, unknown, BigDecimal.TEN, REASON, EconomyOperationId.random())
                        .toCompletableFuture());
        verifyNoInteractions(accountRepository);
    }

    @Test
    void shouldRejectSameAccountTransferSynchronously() {
        var service = newService();

        assertCause(
                SameEconomyAccountTransferException.class,
                service.transfer(USER_ID, USER_ID, CURRENCY, BigDecimal.TEN, REASON, EconomyOperationId.random())
                        .toCompletableFuture());
        verifyNoInteractions(transferRepository);
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullArgumentsForRemainingOperations() {
        var service = newService();

        assertCause(NullPointerException.class, service.balance(null).toCompletableFuture());
        assertCause(NullPointerException.class, service.balance(null, CURRENCY).toCompletableFuture());
        assertCause(
                NullPointerException.class, service.has(null, BigDecimal.TEN).toCompletableFuture());
        assertCause(NullPointerException.class, service.has(USER_ID, null).toCompletableFuture());
        assertCause(
                NullPointerException.class, service.has(USER_ID, CURRENCY, null).toCompletableFuture());
        assertCause(
                NullPointerException.class,
                service.withdraw(null, BigDecimal.TEN, REASON, EconomyOperationId.random())
                        .toCompletableFuture());
        assertCause(
                NullPointerException.class,
                service.set(null, BigDecimal.TEN, REASON, EconomyOperationId.random())
                        .toCompletableFuture());
        assertCause(
                NullPointerException.class,
                service.transfer(null, OTHER_USER_ID, BigDecimal.TEN, REASON, EconomyOperationId.random())
                        .toCompletableFuture());
        assertCause(
                NullPointerException.class,
                service.transfer(USER_ID, null, BigDecimal.TEN, REASON, EconomyOperationId.random())
                        .toCompletableFuture());

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(transferRepository);
    }
}
