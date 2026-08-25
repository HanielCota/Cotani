package com.cotani.trade.api;

/** Raised when an operation is invalid for the current trade lifecycle state. */
public final class TradeStateException extends TradeException {
    private static final long serialVersionUID = 1L;

    public TradeStateException(TradeId tradeId, TradeStatus status) {
        super("Trade " + java.util.Objects.requireNonNull(tradeId, "tradeId") + " is not open (status="
                + java.util.Objects.requireNonNull(status, "status") + ")");
    }
}
