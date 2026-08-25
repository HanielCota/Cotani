package com.cotani.trade.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Immutable state loaded from a trade repository. */
public record TradeSnapshot(List<TradeSession> trades) {
    public TradeSnapshot {
        Objects.requireNonNull(trades, "trades");
        var ids = new HashSet<TradeId>();
        trades.forEach(trade -> {
            Objects.requireNonNull(trade, "trade");
            if (!ids.add(trade.id())) {
                throw new IllegalArgumentException("snapshot contains duplicate trade ids");
            }
        });
        trades = List.copyOf(trades);
    }

    public static TradeSnapshot empty() {
        return new TradeSnapshot(List.of());
    }
}
