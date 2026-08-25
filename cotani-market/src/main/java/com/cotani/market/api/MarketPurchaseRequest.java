package com.cotani.market.api;

import java.util.Objects;
import java.util.UUID;

/** Caller-owned purchase intent; retries must reuse the same purchase id. */
public record MarketPurchaseRequest(MarketPurchaseId purchaseId, MarketListingId listingId, UUID buyerId) {
    public MarketPurchaseRequest {
        Objects.requireNonNull(purchaseId, "purchaseId");
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(buyerId, "buyerId");
    }

    public static MarketPurchaseRequest create(MarketListingId listingId, UUID buyerId) {
        return new MarketPurchaseRequest(MarketPurchaseId.random(), listingId, buyerId);
    }
}
