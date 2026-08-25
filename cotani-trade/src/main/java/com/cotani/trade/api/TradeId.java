package com.cotani.trade.api;

import java.util.Objects;
import java.util.UUID;

/** Stable identity and idempotency key for one trade session. */
public record TradeId(UUID value) {
    public TradeId {
        Objects.requireNonNull(value, "value");
    }

    public static TradeId random() {
        return new TradeId(UUID.randomUUID());
    }

    public static TradeId of(UUID value) {
        return new TradeId(value);
    }
}
