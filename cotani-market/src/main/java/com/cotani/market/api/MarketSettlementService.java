package com.cotani.market.api;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Host-owned idempotent adapter that charges currency and delivers the item. */
public interface MarketSettlementService {
    /**
     * Settles the purchase exactly once from the adapter's perspective.
     * Implementations must key all external side effects by {@link MarketPurchaseId}.
     */
    CompletionStage<Void> settleAsync(MarketPurchase purchase);

    /**
     * Reports whether external effects exist for a pending purchase.
     *
     * <p>The default is deliberately conservative: an adapter that cannot prove that no external
     * effect exists must not allow the marketplace to release the reservation.
     */
    default CompletionStage<MarketSettlementStatus> statusAsync(MarketPurchase purchase) {
        return CompletableFuture.completedFuture(MarketSettlementStatus.UNKNOWN);
    }
}
