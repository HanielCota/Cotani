package com.cotani.market.api.event;

import com.cotani.market.api.MarketPurchase;
import java.util.Objects;

/** Published after settlement and durable marketplace completion. */
public record PurchaseSettledEvent(MarketPurchase purchase) implements MarketEvent {
    public PurchaseSettledEvent {
        Objects.requireNonNull(purchase, "purchase");
    }
}
