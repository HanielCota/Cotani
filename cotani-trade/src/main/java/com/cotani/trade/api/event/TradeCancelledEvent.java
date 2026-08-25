package com.cotani.trade.api.event;

import com.cotani.trade.api.TradeSession;
import java.util.Objects;

/** Published after an open trade is cancelled. */
public record TradeCancelledEvent(TradeSession trade) implements TradeEvent {
    public TradeCancelledEvent {
        Objects.requireNonNull(trade, "trade");
    }
}
