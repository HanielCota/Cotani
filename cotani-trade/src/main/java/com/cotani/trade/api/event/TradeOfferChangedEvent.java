package com.cotani.trade.api.event;

import com.cotani.trade.api.TradeSession;
import java.util.Objects;

/** Published after a participant changes their offer. */
public record TradeOfferChangedEvent(TradeSession trade) implements TradeEvent {
    public TradeOfferChangedEvent {
        Objects.requireNonNull(trade, "trade");
    }
}
