package com.cotani.market.api.event;

import com.cotani.market.api.MarketPurchase;
import java.util.Objects;

/** Published after a pending purchase is safely released. */
public record PurchaseReleasedEvent(MarketPurchase purchase) implements MarketEvent {
    public PurchaseReleasedEvent {
        Objects.requireNonNull(purchase, "purchase");
    }
}
