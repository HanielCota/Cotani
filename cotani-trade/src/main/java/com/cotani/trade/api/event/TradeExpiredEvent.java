package com.cotani.trade.api.event;

import com.cotani.trade.api.TradeSession;
import java.util.Objects;

/** Published when an expired trade is observed by a subsequent mutation. */
public record TradeExpiredEvent(TradeSession trade) implements TradeEvent {
    public TradeExpiredEvent {
        Objects.requireNonNull(trade, "trade");
    }
}
