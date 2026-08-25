package com.cotani.market.api;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for one marketplace listing. */
public record MarketListingId(UUID value) {
    public MarketListingId {
        Objects.requireNonNull(value, "value");
    }

    public static MarketListingId random() {
        return new MarketListingId(UUID.randomUUID());
    }
}
