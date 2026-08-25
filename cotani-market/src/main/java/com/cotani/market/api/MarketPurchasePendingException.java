package com.cotani.market.api;

/** Indicates that settlement timed out or failed after durable reservation; retry the same purchase id. */
public final class MarketPurchasePendingException extends MarketException {
    private static final long serialVersionUID = 1L;

    public MarketPurchasePendingException(MarketPurchaseId purchaseId, Throwable cause) {
        super("market purchase remains pending and must be recovered: " + purchaseId.value(), cause);
    }
}
