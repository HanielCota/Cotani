package com.cotani.trade.api;

import java.util.Objects;

/** Immutable settlement command with the trade id as an idempotency key. */
public record TradeSettlement(TradeId tradeId, TradeOffer initiatorOffer, TradeOffer recipientOffer) {
    public TradeSettlement {
        Objects.requireNonNull(tradeId, "tradeId");
        Objects.requireNonNull(initiatorOffer, "initiatorOffer");
        Objects.requireNonNull(recipientOffer, "recipientOffer");
    }
}
