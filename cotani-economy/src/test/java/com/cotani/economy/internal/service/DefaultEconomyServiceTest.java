package com.cotani.economy.internal.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cotani.economy.EconomySettings;
import com.cotani.economy.account.EconomyAccount;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.currency.EconomyCurrency;
import com.cotani.economy.event.EconomyEventPublisher;
import com.cotani.economy.event.EconomyTransactionEvent;
import com.cotani.economy.exception.InsufficientFundsException;
import com.cotani.economy.exception.SameEconomyAccountTransferException;
import com.cotani.economy.internal.protection.DefaultEconomyGuard;
import com.cotani.economy.internal.repository.EconomyAccountRepository;
import com.cotani.economy.internal.repository.EconomyTransferRepository;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class DefaultEconomyServiceTest {

    private static final EconomySettings SETTINGS = EconomySettings.defaultSettings(EconomyCurrency.coins());
    private static final CurrencyId CURRENCY = SETTINGS.defaultCurrency().id();
    private static final EconomyReason REASON = EconomyReason.system("test");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final EconomyOperationId OP_ID = EconomyOperationId.random();
    private static final BigDecimal NORMALIZED_TEN =
            BigDecimal.TEN.setScale(SETTINGS.defaultCurrency().decimalPlaces());

    private final EconomyAccountRepository accountRepository = Mockito.mock(EconomyAccountRepository.class);
    private final EconomyTransferRepository transferRepository = Mockito.mock(EconomyTransferRepository.class);
    private final EconomyEventPublisher eventPublisher = Mockito.mock(EconomyEventPublisher.class);

    private DefaultEconomyService newService() {
        return new DefaultEconomyService(
                SETTINGS, new DefaultEconomyGuard(SETTINGS), accountRepository, transferRepository, eventPublisher);
    }

    private static EconomyTransaction sampleDeposit() {
        return EconomyTransaction.deposit(
                OP_ID, USER_ID, CURRENCY, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, REASON, Instant.now());
    }

    private static <T extends Throwable> T assertCause(Class<T> type, CompletableFuture<?> future) {
        var exception = assertThrows(CompletionException.class, future::join);
        assertNotNull(exception.getCause());
        assertInstanceOf(type, exception.getCause());
        return type.cast(exception.getCause());
    }

    @Test
    void balanceQueriesAccountRepository() {
        var service = newService();
        var account = EconomyAccount.create(USER_ID, CURRENCY, BigDecimal.valueOf(42), Instant.now());
        when(accountRepository.getOrCreate(USER_ID, CURRENCY)).thenReturn(CompletableFuture.completedFuture(account));

        var balance = service.balance(USER_ID, CURRENCY).toCompletableFuture().join();

        assertEquals(0, balance.amount().compareTo(BigDecimal.valueOf(42)));
        verify(accountRepository).getOrCreate(USER_ID, CURRENCY);
    }

    @Test
    void hasReturnsTrueWhenBalanceIsEnough() {
        var service = newService();
        var account = EconomyAccount.create(USER_ID, CURRENCY, BigDecimal.valueOf(100), Instant.now());
        when(accountRepository.getOrCreate(USER_ID, CURRENCY)).thenReturn(CompletableFuture.completedFuture(account));

        var result = service.has(USER_ID, BigDecimal.TEN).toCompletableFuture().join();

        assertTrue(result);
    }

    @Test
    void hasReturnsFalseWhenBalanceIsInsufficient() {
        var service = newService();
        var account = EconomyAccount.create(USER_ID, CURRENCY, BigDecimal.ONE, Instant.now());
        when(accountRepository.getOrCreate(USER_ID, CURRENCY)).thenReturn(CompletableFuture.completedFuture(account));

        var result = service.has(USER_ID, BigDecimal.TEN).toCompletableFuture().join();

        assertFalse(result);
    }

    @Test
    void depositValidatesInputAndPublishesEvent() {
        var service = newService();
        var transaction = sampleDeposit();
        when(accountRepository.deposit(USER_ID, CURRENCY, NORMALIZED_TEN, REASON, OP_ID))
                .thenReturn(CompletableFuture.completedFuture(transaction));

        var result = service.deposit(USER_ID, CURRENCY, BigDecimal.TEN, REASON, OP_ID)
                .toCompletableFuture()
                .join();

        assertEquals(transaction, result);
        verify(accountRepository).deposit(USER_ID, CURRENCY, NORMALIZED_TEN, REASON, OP_ID);
        var captor = ArgumentCaptor.forClass(EconomyTransactionEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertEquals(transaction, captor.getValue().transaction());
    }

    @Test
    void depositWithDefaultCurrencyDelegatesToCurrencyOverload() {
        var service = newService();
        var transaction = sampleDeposit();
        when(accountRepository.deposit(USER_ID, CURRENCY, NORMALIZED_TEN, REASON, OP_ID))
                .thenReturn(CompletableFuture.completedFuture(transaction));

        var result = service.deposit(USER_ID, BigDecimal.TEN, REASON, OP_ID)
                .toCompletableFuture()
                .join();

        assertEquals(transaction, result);
        verify(accountRepository).deposit(USER_ID, CURRENCY, NORMALIZED_TEN, REASON, OP_ID);
    }

    @Test
    void depositPropagatesRepositoryFailure() {
        var service = newService();
        var failure = new InsufficientFundsException(USER_ID, BigDecimal.TEN, BigDecimal.ZERO);
        when(accountRepository.deposit(USER_ID, CURRENCY, NORMALIZED_TEN, REASON, OP_ID))
                .thenReturn(CompletableFuture.failedFuture(failure));

        assertCause(
                InsufficientFundsException.class,
                service.deposit(USER_ID, CURRENCY, BigDecimal.TEN, REASON, OP_ID)
                        .toCompletableFuture());
    }

    @Test
    void publisherExceptionDoesNotBreakTransactionResult() {
        var service = newService();
        var transaction = sampleDeposit();
        when(accountRepository.deposit(USER_ID, CURRENCY, NORMALIZED_TEN, REASON, OP_ID))
                .thenReturn(CompletableFuture.completedFuture(transaction));
        doThrow(new RuntimeException("boom")).when(eventPublisher).publish(any(EconomyTransactionEvent.class));

        var result = service.deposit(USER_ID, CURRENCY, BigDecimal.TEN, REASON, OP_ID)
                .toCompletableFuture()
                .join();

        assertEquals(transaction, result);
        verify(eventPublisher).publish(any(EconomyTransactionEvent.class));
    }

    @Test
    void withdrawValidatesInputAndReturnsTransaction() {
        var service = newService();
        var transaction = EconomyTransaction.withdraw(
                OP_ID, USER_ID, CURRENCY, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, REASON, Instant.now());
        when(accountRepository.withdraw(USER_ID, CURRENCY, NORMALIZED_TEN, REASON, OP_ID))
                .thenReturn(CompletableFuture.completedFuture(transaction));

        var result = service.withdraw(USER_ID, CURRENCY, BigDecimal.TEN, REASON, OP_ID)
                .toCompletableFuture()
                .join();

        assertEquals(transaction, result);
        verify(accountRepository).withdraw(USER_ID, CURRENCY, NORMALIZED_TEN, REASON, OP_ID);
    }

    @Test
    void setValidatesInputAndReturnsTransaction() {
        var service = newService();
        var transaction = EconomyTransaction.set(
                OP_ID, USER_ID, CURRENCY, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, REASON, Instant.now());
        when(accountRepository.set(USER_ID, CURRENCY, NORMALIZED_TEN, REASON, OP_ID))
                .thenReturn(CompletableFuture.completedFuture(transaction));

        var result = service.set(USER_ID, CURRENCY, BigDecimal.TEN, REASON, OP_ID)
                .toCompletableFuture()
                .join();

        assertEquals(transaction, result);
        verify(accountRepository).set(USER_ID, CURRENCY, NORMALIZED_TEN, REASON, OP_ID);
    }

    @Test
    void transferValidatesInputAndReturnsTransaction() {
        var service = newService();
        var source = UUID.randomUUID();
        var target = UUID.randomUUID();
        var transaction = EconomyTransaction.transfer(
                OP_ID,
                source,
                target,
                CURRENCY,
                BigDecimal.TEN,
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(10),
                BigDecimal.ZERO,
                BigDecimal.TEN,
                REASON,
                Instant.now());
        when(transferRepository.transfer(source, target, CURRENCY, NORMALIZED_TEN, REASON, OP_ID))
                .thenReturn(CompletableFuture.completedFuture(transaction));

        var result = service.transfer(source, target, CURRENCY, BigDecimal.TEN, REASON, OP_ID)
                .toCompletableFuture()
                .join();

        assertEquals(transaction, result);
        verify(transferRepository).transfer(source, target, CURRENCY, NORMALIZED_TEN, REASON, OP_ID);
    }

    @Test
    void transferToSameUserThrowsSynchronousValidationError() {
        var service = newService();

        assertThrows(
                SameEconomyAccountTransferException.class,
                () -> service.transfer(USER_ID, USER_ID, BigDecimal.TEN, REASON, OP_ID));
        verifyNoInteractions(transferRepository);
    }

    @Test
    @SuppressWarnings("NullAway")
    void nullArgumentsAreRejectedBeforeRepositoryCall() {
        var service = newService();

        assertThrows(NullPointerException.class, () -> service.deposit(null, CURRENCY, BigDecimal.TEN, REASON, OP_ID));
        assertThrows(NullPointerException.class, () -> service.deposit(USER_ID, null, BigDecimal.TEN, REASON, OP_ID));
        assertThrows(NullPointerException.class, () -> service.deposit(USER_ID, CURRENCY, null, REASON, OP_ID));
        assertThrows(NullPointerException.class, () -> service.deposit(USER_ID, CURRENCY, BigDecimal.TEN, null, OP_ID));
        assertThrows(
                NullPointerException.class, () -> service.deposit(USER_ID, CURRENCY, BigDecimal.TEN, REASON, null));

        verifyNoInteractions(accountRepository);
    }
}
