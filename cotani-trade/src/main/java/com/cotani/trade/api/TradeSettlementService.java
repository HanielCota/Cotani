package com.cotani.trade.api;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** SPI that atomically exchanges both offers and safely handles repeated settlement calls. */
public interface TradeSettlementService {
    /**
     * Settles one trade without blocking the caller.
     *
     * <p>Implementations must atomically transfer both offers or neither offer and must treat
     * {@link TradeSettlement#tradeId()} as an idempotency key. An exceptional completion before
     * the deadline must mean that the exchange was not committed; an unknown outcome must instead
     * be represented by a late completion or by {@link #statusAsync(TradeId)}. Implementations
     * must not retain live Bukkit objects in the asynchronous operation.
     */
    CompletionStage<Void> settleAsync(TradeSettlement settlement);

    /**
     * Returns the durable settlement status for recovery.
     *
     * <p>Adapters that cannot query durable state must return {@link TradeSettlementStatus#UNKNOWN}
     * and the trade remains pending rather than being marked failed.
     */
    default CompletionStage<TradeSettlementStatus> statusAsync(TradeId tradeId) {
        Objects.requireNonNull(tradeId, "tradeId");
        return CompletableFuture.completedFuture(TradeSettlementStatus.UNKNOWN);
    }
}
