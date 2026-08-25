package com.cotani.market.api;

/** Raised when a seller attempts to purchase their own listing. */
public final class MarketSelfPurchaseException extends MarketException {
    private static final long serialVersionUID = 1L;

    public MarketSelfPurchaseException(MarketListingId listingId) {
        super("seller cannot purchase their own listing: " + listingId.value());
    }
}
