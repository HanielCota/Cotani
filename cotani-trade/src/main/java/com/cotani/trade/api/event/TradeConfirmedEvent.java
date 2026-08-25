package com.cotani.trade.api.event;

import com.cotani.trade.api.TradeSession;
import java.util.Objects;

/** Published after one participant confirms the current offer revision. */
public record TradeConfirmedEvent(TradeSession trade) implements TradeEvent {
    public TradeConfirmedEvent {
        Objects.requireNonNull(trade, "trade");
    }
}
