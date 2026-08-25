package com.cotani.trade.api.event;

import com.cotani.trade.api.TradeSession;
import java.util.Objects;

/** Published after the settlement adapter completes successfully. */
public record TradeCompletedEvent(TradeSession trade) implements TradeEvent {
    public TradeCompletedEvent {
        Objects.requireNonNull(trade, "trade");
    }
}
