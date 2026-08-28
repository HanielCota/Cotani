package com.cotani.reward.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.economy.EconomyService;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.transaction.EconomyBalanceChange;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import com.cotani.economy.transaction.EconomyTransactionDetails;
import com.cotani.reward.api.CurrencyGrant;
import com.cotani.reward.api.ItemGrant;
import com.cotani.reward.api.RewardClaimId;
import com.cotani.reward.api.RewardGrantHandler.RewardSettlementContext;
import com.cotani.reward.api.RewardId;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RewardEconomyGrantHandlerTest {
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLAIM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private EconomyService economyService;
    private RewardEconomyGrantHandler handler;

    @BeforeEach
    void setUp() {
        economyService = mock(EconomyService.class);
        handler = new RewardEconomyGrantHandler(economyService);
        when(economyService.deposit(
                        any(), any(CurrencyId.class), any(BigDecimal.class), any(), any(EconomyOperationId.class)))
                .thenReturn(CompletableFuture.completedFuture(transaction()));
    }

    @Test
    void supportsOnlyCurrencyGrants() {
        assertTrue(handler.supports(new CurrencyGrant("coins", new BigDecimal("12.50"))));
        assertFalse(handler.supports(new ItemGrant("diamond", 1)));
    }

    @Test
    void settlesCurrencyWithTheExpectedEconomyRequest() {
        var grant = new CurrencyGrant(" COINS ", new BigDecimal("12.50"));
        var context = context(3);

        handler.settleAsync(context, grant).toCompletableFuture().join();

        var operationCaptor = ArgumentCaptor.forClass(EconomyOperationId.class);
        verify(economyService)
                .deposit(
                        org.mockito.ArgumentMatchers.eq(PLAYER_ID),
                        org.mockito.ArgumentMatchers.eq(CurrencyId.of("coins")),
                        org.mockito.ArgumentMatchers.eq(new BigDecimal("12.5")),
                        any(),
                        operationCaptor.capture());

        var expectedValue = context.claimId().value() + ":currency:" + context.grantIndex();
        var expectedOperation =
                EconomyOperationId.of(UUID.nameUUIDFromBytes(expectedValue.getBytes(StandardCharsets.UTF_8)));
        assertEquals(expectedOperation, operationCaptor.getValue());
    }

    @Test
    void createsDifferentOperationIdsForDifferentGrantIndexes() {
        var grant = new CurrencyGrant("coins", BigDecimal.ONE);

        handler.settleAsync(context(0), grant).toCompletableFuture().join();
        handler.settleAsync(context(1), grant).toCompletableFuture().join();

        var operationCaptor = ArgumentCaptor.forClass(EconomyOperationId.class);
        verify(economyService, org.mockito.Mockito.times(2))
                .deposit(any(), any(CurrencyId.class), any(BigDecimal.class), any(), operationCaptor.capture());
        assertEquals(2, operationCaptor.getAllValues().stream().distinct().count());
    }

    @Test
    void returnsFailedStageForUnsupportedGrant() {
        var failure = org.junit.jupiter.api.Assertions.assertThrows(
                CompletionException.class,
                () -> handler.settleAsync(context(0), new ItemGrant("diamond", 1))
                        .toCompletableFuture()
                        .join());

        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        verify(economyService, never())
                .deposit(any(), any(CurrencyId.class), any(BigDecimal.class), any(), any(EconomyOperationId.class));
    }

    @Test
    void propagatesEconomyFailure() {
        var failure = new IllegalStateException("economy unavailable");
        when(economyService.deposit(
                        any(), any(CurrencyId.class), any(BigDecimal.class), any(), any(EconomyOperationId.class)))
                .thenReturn(CompletableFuture.failedFuture(failure));

        var thrown = org.junit.jupiter.api.Assertions.assertThrows(
                CompletionException.class,
                () -> handler.settleAsync(context(0), new CurrencyGrant("coins", BigDecimal.ONE))
                        .toCompletableFuture()
                        .join());

        assertEquals(failure, thrown.getCause());
    }

    private static RewardSettlementContext context(int grantIndex) {
        return new RewardSettlementContext(PLAYER_ID, new RewardClaimId(CLAIM_ID), new RewardId("daily"), grantIndex);
    }

    private static EconomyTransaction transaction() {
        var details = new EconomyTransactionDetails(
                EconomyOperationId.random(),
                CurrencyId.of("coins"),
                BigDecimal.ONE,
                EconomyReason.system("test"),
                Instant.EPOCH);
        var change = new EconomyBalanceChange(PLAYER_ID, BigDecimal.ZERO, BigDecimal.ONE);
        return EconomyTransaction.deposit(details, change);
    }
}
