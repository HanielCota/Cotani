package com.cotani.trade.api;

/** Raised when a requested trade does not exist. */
public final class TradeNotFoundException extends TradeException {
    private static final long serialVersionUID = 1L;

    public TradeNotFoundException(TradeId tradeId) {
        super("Trade not found: " + java.util.Objects.requireNonNull(tradeId, "tradeId"));
    }
}
