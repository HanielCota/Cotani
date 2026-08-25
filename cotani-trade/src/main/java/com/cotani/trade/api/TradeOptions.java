package com.cotani.trade.api;

import java.time.Duration;
import java.util.Objects;

/** Options captured when a trade is created. */
public record TradeOptions(Duration lifetime) {
    public TradeOptions {
        Objects.requireNonNull(lifetime, "lifetime");
        if (lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException("lifetime must be positive");
        }
    }

    public static TradeOptions defaults() {
        return new TradeOptions(Duration.ofMinutes(2));
    }

    public TradeOptions withLifetime(Duration nextLifetime) {
        return new TradeOptions(nextLifetime);
    }
}
