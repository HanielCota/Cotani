package com.cotani.trade.api;

/** Raised when a player that is not a participant attempts to operate a trade. */
public final class TradeAccessDeniedException extends TradeException {
    private static final long serialVersionUID = 1L;

    public TradeAccessDeniedException(TradeId tradeId) {
        super("Player is not a participant of trade " + java.util.Objects.requireNonNull(tradeId, "tradeId"));
    }
}
