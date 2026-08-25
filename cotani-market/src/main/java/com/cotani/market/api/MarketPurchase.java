package com.cotani.market.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable purchase receipt and settlement payload. */
public record MarketPurchase(
        MarketPurchaseId id,
        MarketListingId listingId,
        UUID sellerId,
        UUID buyerId,
        MarketItem item,
        MarketPrice price,
        Instant reservedAt,
        MarketPurchaseStatus status,
        Optional<Instant> settledAt) {
    public MarketPurchase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(buyerId, "buyerId");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(reservedAt, "reservedAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(settledAt, "settledAt");
        if (status == MarketPurchaseStatus.SETTLED && settledAt.isEmpty()) {
            throw new IllegalArgumentException("settled purchase must have settledAt");
        }
        if (status == MarketPurchaseStatus.PENDING && settledAt.isPresent()) {
            throw new IllegalArgumentException("pending purchase must not have settledAt");
        }
        if (status == MarketPurchaseStatus.CANCELLED && settledAt.isPresent()) {
            throw new IllegalArgumentException("cancelled purchase must not have settledAt");
        }
    }

    public static MarketPurchase pending(MarketPurchaseRequest request, MarketListing listing, Instant reservedAt) {
        return new MarketPurchase(
                request.purchaseId(),
                listing.id(),
                listing.sellerId(),
                request.buyerId(),
                listing.item(),
                listing.price(),
                reservedAt,
                MarketPurchaseStatus.PENDING,
                Optional.empty());
    }

    public MarketPurchase settled(Instant completedAt) {
        return new MarketPurchase(
                id,
                listingId,
                sellerId,
                buyerId,
                item,
                price,
                reservedAt,
                MarketPurchaseStatus.SETTLED,
                Optional.of(Objects.requireNonNull(completedAt, "completedAt")));
    }

    public MarketPurchase cancelled() {
        return new MarketPurchase(
                id,
                listingId,
                sellerId,
                buyerId,
                item,
                price,
                reservedAt,
                MarketPurchaseStatus.CANCELLED,
                Optional.empty());
    }
}
