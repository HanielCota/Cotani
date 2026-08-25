package com.cotani.market.api;

import java.util.Objects;
import java.util.UUID;

/** Stable idempotency key for one marketplace purchase attempt. */
public record MarketPurchaseId(UUID value) {
    public MarketPurchaseId {
        Objects.requireNonNull(value, "value");
    }

    public static MarketPurchaseId random() {
        return new MarketPurchaseId(UUID.randomUUID());
    }
}
