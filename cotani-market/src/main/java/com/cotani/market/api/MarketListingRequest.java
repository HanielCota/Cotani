package com.cotani.market.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Caller-owned immutable listing intent with a stable idempotency key. */
public record MarketListingRequest(
        MarketListingId id, UUID sellerId, MarketItem item, MarketPrice price, Instant createdAt, Instant expiresAt) {
    public MarketListingRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }

    public static MarketListingRequest create(
            UUID sellerId, MarketItem item, MarketPrice price, Duration duration, Instant createdAt) {
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(createdAt, "createdAt");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        try {
            return new MarketListingRequest(
                    MarketListingId.random(), sellerId, item, price, createdAt, createdAt.plus(duration));
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException("duration is too large", failure);
        }
    }
}
