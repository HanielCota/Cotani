package com.cotani.market.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable listing snapshot returned by the marketplace. */
public record MarketListing(
        MarketListingId id,
        UUID sellerId,
        MarketItem item,
        MarketPrice price,
        Instant createdAt,
        Instant expiresAt,
        MarketListingStatus status) {
    public MarketListing {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(status, "status");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }

    public static MarketListing active(MarketListingRequest request) {
        Objects.requireNonNull(request, "request");
        return new MarketListing(
                request.id(),
                request.sellerId(),
                request.item(),
                request.price(),
                request.createdAt(),
                request.expiresAt(),
                MarketListingStatus.ACTIVE);
    }

    public boolean isActiveAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return status == MarketListingStatus.ACTIVE && expiresAt.isAfter(now);
    }

    public MarketListing withStatus(MarketListingStatus newStatus) {
        return new MarketListing(id, sellerId, item, price, createdAt, expiresAt, newStatus);
    }
}
