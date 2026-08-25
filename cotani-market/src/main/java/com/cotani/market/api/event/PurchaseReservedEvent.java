package com.cotani.market.api.event;

import com.cotani.market.api.MarketPurchase;
import java.util.Objects;

/** Published after a listing is durably reserved for a buyer. */
public record PurchaseReservedEvent(MarketPurchase purchase) implements MarketEvent {
    public PurchaseReservedEvent {
        Objects.requireNonNull(purchase, "purchase");
    }
}
