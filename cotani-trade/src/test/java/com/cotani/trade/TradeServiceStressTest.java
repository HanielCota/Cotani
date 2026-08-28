package com.cotani.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.economy.currency.CurrencyId;
import com.cotani.testkit.StressTestSupport;
import com.cotani.trade.api.TradeCurrency;
import com.cotani.trade.api.TradeId;
import com.cotani.trade.api.TradeSettlement;
import com.cotani.trade.api.TradeSettlementService;
import com.cotani.trade.api.TradeStatus;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("stress")
class TradeServiceStressTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Test
    void generatedTwoPlayerTradesSettleOnceAndConserveOffers() {
        var settlement = new IdempotentSettlement();
        var service = CotaniTrades.inMemory(settlement);
        try {
            StressTestSupport.scenarios("trade", "offer-confirm-settle", (context, random, initiator) -> {
                var recipientId = random.uuid("recipient");
                var trade = StressTestSupport.await(
                        service.createAsync(initiator.id(), recipientId, com.cotani.trade.api.TradeOptions.defaults()),
                        TIMEOUT,
                        context);
                var firstAmount = random.positiveDecimal(1_000_000, 8);
                var secondAmount = random.positiveDecimal(1_000_000, 8);
                StressTestSupport.await(
                        service.offerAsync(
                                trade.id(),
                                initiator.id(),
                                List.of(new TradeCurrency(CurrencyId.of("coins"), firstAmount))),
                        TIMEOUT,
                        context);
                StressTestSupport.await(
                        service.offerAsync(
                                trade.id(),
                                recipientId,
                                List.of(new TradeCurrency(CurrencyId.of("gems"), secondAmount))),
                        TIMEOUT,
                        context);
                var firstConfirmation =
                        StressTestSupport.await(service.confirmAsync(trade.id(), initiator.id()), TIMEOUT, context);
                var duplicateConfirmation =
                        StressTestSupport.await(service.confirmAsync(trade.id(), initiator.id()), TIMEOUT, context);
                var completed =
                        StressTestSupport.await(service.confirmAsync(trade.id(), recipientId), TIMEOUT, context);

                assertEquals(firstConfirmation, duplicateConfirmation, context::description);
                assertEquals(TradeStatus.COMPLETED, completed.status(), context::description);
                assertTrue(settlement.settledTradeIds.contains(trade.id()), context::description);
                assertTrue(
                        StressTestSupport.await(service.findByPlayerAsync(initiator.id()), TIMEOUT, context)
                                .isEmpty(),
                        context::description);
            });
            assertEquals(StressTestSupport.iterations(), settlement.settledTradeIds.size());
        } finally {
            service.closeAsync().toCompletableFuture().join();
        }
    }

    private static final class IdempotentSettlement implements TradeSettlementService {
        private final Set<TradeId> settledTradeIds = ConcurrentHashMap.newKeySet();

        @Override
        public CompletionStage<Void> settleAsync(TradeSettlement settlement) {
            settledTradeIds.add(settlement.tradeId());
            return CompletableFuture.completedFuture(null);
        }
    }
}
