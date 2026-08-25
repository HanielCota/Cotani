package com.cotani.market.api;

/** Raised when a listing cannot perform the requested lifecycle transition. */
public final class MarketListingStateException extends MarketException {
    private static final long serialVersionUID = 1L;

    public MarketListingStateException(MarketListingId listingId, MarketListingStatus status) {
        super("listing " + listingId.value() + " is not purchasable or cancellable in state " + status);
    }
}
