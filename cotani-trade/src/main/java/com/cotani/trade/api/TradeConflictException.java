package com.cotani.trade.api;

/** Raised when a participant already owns another active trade or an operation conflicts. */
public final class TradeConflictException extends TradeException {
    private static final long serialVersionUID = 1L;

    public TradeConflictException(String message) {
        super(message);
    }
}
