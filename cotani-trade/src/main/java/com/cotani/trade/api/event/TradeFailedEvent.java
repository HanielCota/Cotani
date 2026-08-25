package com.cotani.trade.api.event;

import com.cotani.trade.api.TradeSession;
import java.util.Objects;

/** Published after settlement fails and the trade becomes terminal. */
public record TradeFailedEvent(TradeSession trade) implements TradeEvent {
    public TradeFailedEvent {
        Objects.requireNonNull(trade, "trade");
    }
}
