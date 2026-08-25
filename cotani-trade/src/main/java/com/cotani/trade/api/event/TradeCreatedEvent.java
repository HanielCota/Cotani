package com.cotani.trade.api.event;

import com.cotani.trade.api.TradeSession;
import java.util.Objects;

/** Published after a trade is persisted. */
public record TradeCreatedEvent(TradeSession trade) implements TradeEvent {
    public TradeCreatedEvent {
        Objects.requireNonNull(trade, "trade");
    }
}
