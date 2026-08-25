package com.cotani.market.api;

/** Raised when a listing or purchase cannot be found. */
public final class MarketNotFoundException extends MarketException {
    private static final long serialVersionUID = 1L;

    public MarketNotFoundException(String entity, Object id) {
        super(entity + " not found: " + id);
    }
}
