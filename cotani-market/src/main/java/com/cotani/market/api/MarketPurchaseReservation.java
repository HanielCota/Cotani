package com.cotani.market.api;

import java.util.Objects;

/** Result of an idempotent reservation, including whether a new receipt was created. */
public record MarketPurchaseReservation(MarketPurchase purchase, boolean created) {
    public MarketPurchaseReservation {
        Objects.requireNonNull(purchase, "purchase");
    }
}
