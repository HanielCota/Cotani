package com.cotani.market.internal;

import com.cotani.api.InternalApi;
import com.cotani.market.api.MarketConflictException;
import com.cotani.market.api.MarketListing;
import com.cotani.market.api.MarketListingId;
import com.cotani.market.api.MarketListingStateException;
import com.cotani.market.api.MarketListingStatus;
import com.cotani.market.api.MarketNotFoundException;
import com.cotani.market.api.MarketPage;
import com.cotani.market.api.MarketPurchase;
import com.cotani.market.api.MarketPurchaseId;
import com.cotani.market.api.MarketPurchaseRequest;
import com.cotani.market.api.MarketPurchaseReservation;
import com.cotani.market.api.MarketPurchaseStatus;
import com.cotani.market.api.MarketQuery;
import com.cotani.market.api.MarketRepository;
import com.cotani.market.api.MarketSelfPurchaseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Thread-safe in-memory repository for tests and ephemeral servers. */
@InternalApi
public final class InMemoryMarketRepository implements MarketRepository {
    private final Map<MarketListingId, MarketListing> listings = new LinkedHashMap<>();
    private final Map<MarketPurchaseId, MarketPurchase> purchases = new LinkedHashMap<>();

    @Override
    public synchronized CompletionStage<MarketListing> createAsync(MarketListing listing) {
        Objects.requireNonNull(listing, "listing");
        var previous = listings.get(listing.id());
        if (previous != null && !previous.equals(listing)) {
            return failed(new MarketConflictException(listing.id()));
        }
        listings.putIfAbsent(listing.id(), listing);
        return completed(listings.get(listing.id()));
    }

    @Override
    public synchronized CompletionStage<Optional<MarketListing>> findAsync(MarketListingId listingId) {
        Objects.requireNonNull(listingId, "listingId");
        return completed(Optional.ofNullable(listings.get(listingId)));
    }

    @Override
    public synchronized CompletionStage<MarketPage> browseAsync(MarketQuery query, Instant now) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(now, "now");
        var visible = listings.values().stream()
                .filter(listing -> listing.isActiveAt(now))
                .filter(listing ->
                        query.itemKey() == null || listing.item().key().equals(query.itemKey()))
                .filter(listing ->
                        query.currency() == null || listing.price().currency().equals(query.currency()))
                .filter(listing ->
                        query.sellerId() == null || listing.sellerId().equals(query.sellerId()))
                .sorted(Comparator.comparing(MarketListing::createdAt)
                        .reversed()
                        .thenComparing(listing -> listing.id().value().toString(), Comparator.reverseOrder()))
                .toList();
        long offset = (long) query.page() * query.pageSize();
        var page =
                visible.stream().skip(offset).limit((long) query.pageSize() + 1).toList();
        var hasMore = page.size() > query.pageSize();
        return completed(new MarketPage(
                new ArrayList<>(page.subList(0, Math.min(page.size(), query.pageSize()))), hasMore));
    }

    @Override
    public synchronized CompletionStage<MarketListing> cancelAsync(
            UUID sellerId, MarketListingId listingId, Instant now) {
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(now, "now");
        var current = requireListing(listingId);
        if (!current.sellerId().equals(sellerId)) {
            return failed(new MarketListingStateException(listingId, current.status()));
        }
        if (current.status() == MarketListingStatus.ACTIVE
                && !current.expiresAt().isAfter(now)) {
            current = current.withStatus(MarketListingStatus.EXPIRED);
            listings.put(listingId, current);
        }
        if (current.status() != MarketListingStatus.ACTIVE) {
            return failed(new MarketListingStateException(listingId, current.status()));
        }
        var cancelled = current.withStatus(MarketListingStatus.CANCELLED);
        listings.put(listingId, cancelled);
        return completed(cancelled);
    }

    @Override
    public synchronized CompletionStage<MarketPurchaseReservation> reservePurchaseAsync(
            MarketPurchaseRequest request, Instant now) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(now, "now");
        var existing = purchases.get(request.purchaseId());
        if (existing != null) {
            if (!existing.listingId().equals(request.listingId())
                    || !existing.buyerId().equals(request.buyerId())) {
                return failed(new MarketConflictException(request.purchaseId()));
            }
            return completed(new MarketPurchaseReservation(existing, false));
        }
        var listing = requireListing(request.listingId());
        if (listing.sellerId().equals(request.buyerId())) {
            return failed(new MarketSelfPurchaseException(listing.id()));
        }
        if (!listing.isActiveAt(now)) {
            if (listing.status() == MarketListingStatus.ACTIVE
                    && !listing.expiresAt().isAfter(now)) {
                listing = listing.withStatus(MarketListingStatus.EXPIRED);
                listings.put(listing.id(), listing);
            }
            return failed(new MarketListingStateException(listing.id(), listing.status()));
        }
        var pending = MarketPurchase.pending(request, listing, now);
        listings.put(listing.id(), listing.withStatus(MarketListingStatus.PURCHASE_PENDING));
        purchases.put(pending.id(), pending);
        return completed(new MarketPurchaseReservation(pending, true));
    }

    @Override
    public synchronized CompletionStage<Optional<MarketPurchase>> findPurchaseAsync(MarketPurchaseId purchaseId) {
        Objects.requireNonNull(purchaseId, "purchaseId");
        return completed(Optional.ofNullable(purchases.get(purchaseId)));
    }

    @Override
    public synchronized CompletionStage<Boolean> markSettledAsync(MarketPurchaseId purchaseId, Instant settledAt) {
        Objects.requireNonNull(purchaseId, "purchaseId");
        Objects.requireNonNull(settledAt, "settledAt");
        var purchase = purchases.get(purchaseId);
        if (purchase == null) {
            return completed(false);
        }
        if (purchase.status() == MarketPurchaseStatus.SETTLED) {
            return completed(true);
        }
        if (purchase.status() == MarketPurchaseStatus.CANCELLED) {
            return completed(false);
        }
        var listing = listings.get(purchase.listingId());
        if (listing == null || listing.status() != MarketListingStatus.PURCHASE_PENDING) {
            return completed(false);
        }
        purchases.put(purchaseId, purchase.settled(settledAt));
        listings.put(purchase.listingId(), listing.withStatus(MarketListingStatus.SOLD));
        return completed(true);
    }

    @Override
    public synchronized CompletionStage<Boolean> releasePendingAsync(MarketPurchaseId purchaseId, Instant now) {
        Objects.requireNonNull(purchaseId, "purchaseId");
        Objects.requireNonNull(now, "now");
        var purchase = purchases.get(purchaseId);
        if (purchase == null) {
            return completed(false);
        }
        if (purchase.status() == MarketPurchaseStatus.CANCELLED) {
            return completed(true);
        }
        if (purchase.status() != MarketPurchaseStatus.PENDING) {
            return completed(false);
        }
        purchases.put(purchaseId, purchase.cancelled());
        listings.computeIfPresent(purchase.listingId(), (id, listing) -> {
            if (listing.status() != MarketListingStatus.PURCHASE_PENDING) {
                return listing;
            }
            return listing.expiresAt().isAfter(now)
                    ? listing.withStatus(MarketListingStatus.ACTIVE)
                    : listing.withStatus(MarketListingStatus.EXPIRED);
        });
        return completed(true);
    }

    @Override
    public synchronized CompletionStage<List<MarketPurchase>> pendingPurchasesAsync(int limit) {
        validateLimit(limit);
        return completed(purchases.values().stream()
                .filter(purchase -> purchase.status() == MarketPurchaseStatus.PENDING)
                .sorted(Comparator.comparing(MarketPurchase::reservedAt))
                .limit(limit)
                .toList());
    }

    @Override
    public synchronized CompletionStage<Void> purgeAsync(Instant before) {
        Objects.requireNonNull(before, "before");
        purchases
                .entrySet()
                .removeIf(entry -> isTerminal(entry.getValue())
                        && entry.getValue().reservedAt().isBefore(before));
        listings.entrySet()
                .removeIf(entry -> isTerminal(entry.getValue())
                        && entry.getValue().expiresAt().isBefore(before));
        return completedVoid();
    }

    private static boolean isTerminal(MarketPurchase purchase) {
        return purchase.status() != MarketPurchaseStatus.PENDING;
    }

    private static boolean isTerminal(MarketListing listing) {
        return listing.status() != MarketListingStatus.ACTIVE
                && listing.status() != MarketListingStatus.PURCHASE_PENDING;
    }

    private MarketListing requireListing(MarketListingId listingId) {
        return Optional.ofNullable(listings.get(listingId))
                .orElseThrow(() -> new MarketNotFoundException("listing", listingId.value()));
    }

    private static void validateLimit(int limit) {
        if (limit <= 0 || limit > 1_000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
    }

    private static <T> CompletionStage<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }

    private static CompletionStage<Void> completedVoid() {
        return CompletableFuture.completedFuture(null);
    }

    private static <T> CompletionStage<T> failed(Throwable failure) {
        return CompletableFuture.failedFuture(failure);
    }
}
