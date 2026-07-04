package com.cotani.economy.internal.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.cotani.economy.EconomyService;
import com.cotani.economy.EconomySettings;
import com.cotani.economy.account.EconomyBalance;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.currency.EconomyCurrency;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import com.cotani.economy.transaction.EconomyTransactionId;
import com.cotani.task.api.PaperTaskScheduler;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CachedEconomyServiceTest {

    private static final EconomySettings SETTINGS = EconomySettings.defaultSettings(EconomyCurrency.coins());
    private static final UUID USER_ID = UUID.randomUUID();
    private static final CurrencyId CURRENCY = SETTINGS.defaultCurrency().id();
    private static final EconomyBalance BALANCE = new EconomyBalance(USER_ID, CURRENCY, BigDecimal.valueOf(42));

    @Test
    void balanceReturnsCachedValueWithoutCallingDelegate() {
        var delegate = Mockito.mock(EconomyService.class);
        when(delegate.balance(USER_ID, CURRENCY)).thenReturn(CompletableFuture.completedFuture(BALANCE));
        var cache = new CachedEconomyService(delegate, Mockito.mock(PaperTaskScheduler.class), SETTINGS);

        var first = cache.balance(USER_ID, CURRENCY).join();
        var second = cache.balance(USER_ID, CURRENCY).join();

        assertEquals(first, second);
        verify(delegate, times(1)).balance(USER_ID, CURRENCY);
    }

    @Test
    void depositInvalidatesCache() {
        var delegate = Mockito.mock(EconomyService.class);
        when(delegate.balance(USER_ID, CURRENCY)).thenReturn(CompletableFuture.completedFuture(BALANCE));
        var transaction = new EconomyTransaction.Deposit(
                EconomyTransactionId.random(),
                EconomyOperationId.random(),
                USER_ID,
                CURRENCY,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                EconomyReason.system("test"),
                java.time.Instant.now());
        when(delegate.deposit(
                        eq(USER_ID),
                        any(CurrencyId.class),
                        any(BigDecimal.class),
                        any(EconomyReason.class),
                        any(EconomyOperationId.class)))
                .thenReturn(CompletableFuture.completedFuture(transaction));
        var cache = new CachedEconomyService(delegate, Mockito.mock(PaperTaskScheduler.class), SETTINGS);

        cache.balance(USER_ID, CURRENCY).join();
        cache.deposit(USER_ID, BigDecimal.TEN, EconomyReason.system("test"), EconomyOperationId.random())
                .join();
        cache.balance(USER_ID, CURRENCY).join();

        verify(delegate, times(2)).balance(USER_ID, CURRENCY);
    }

    @Test
    void hasUsesConfiguredDefaultCurrency() {
        var delegate = Mockito.mock(EconomyService.class);
        when(delegate.balance(USER_ID, CURRENCY)).thenReturn(CompletableFuture.completedFuture(BALANCE));
        var cache = new CachedEconomyService(delegate, Mockito.mock(PaperTaskScheduler.class), SETTINGS);

        var has = cache.has(USER_ID, BigDecimal.TEN).join();

        assertTrue(has);
        verify(delegate).balance(USER_ID, CURRENCY);
    }

    @Test
    void closeClearsCache() {
        var delegate = Mockito.mock(EconomyService.class);
        when(delegate.balance(USER_ID, CURRENCY)).thenReturn(CompletableFuture.completedFuture(BALANCE));
        var cache = new CachedEconomyService(delegate, Mockito.mock(PaperTaskScheduler.class), SETTINGS);

        cache.balance(USER_ID, CURRENCY).join();
        cache.close();
        cache.balance(USER_ID, CURRENCY).join();

        verify(delegate, times(2)).balance(USER_ID, CURRENCY);
    }
}
