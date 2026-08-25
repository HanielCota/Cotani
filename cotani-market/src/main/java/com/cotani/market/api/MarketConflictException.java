package com.cotani.market.api;

/** Raised when an idempotency key is reused with different data. */
public final class MarketConflictException extends MarketException {
    private static final long serialVersionUID = 1L;

    public MarketConflictException(Object id) {
        super("market idempotency key conflicts with existing data: " + id);
    }
}
