package com.cotani.trade.api;

import java.util.concurrent.CompletionStage;

/** Asynchronous persistence SPI for trade aggregates. */
public interface TradeRepository {
    /** Loads all known trades without blocking the caller. */
    CompletionStage<TradeSnapshot> loadAsync();

    /** Creates a trade and rejects an existing id atomically. */
    CompletionStage<Void> createAsync(TradeSession trade);

    /** Replaces a trade only when its persisted revision equals {@code expectedRevision}. */
    CompletionStage<Void> updateAsync(TradeId tradeId, long expectedRevision, TradeSession trade);
}
