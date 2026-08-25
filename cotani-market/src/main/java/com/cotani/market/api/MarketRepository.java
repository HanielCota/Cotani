package com.cotani.market.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Bukkit-free persistence SPI for listings and idempotent purchase reservations. */
public interface MarketRepository {
    /** Creates a listing; reusing an id with different content must fail. */
    CompletionStage<MarketListing> createAsync(MarketListing listing);

    /** Finds a listing by id, including terminal listings retained for history. */
    CompletionStage<Optional<MarketListing>> findAsync(MarketListingId listingId);

    /** Lists active, non-expired listings at the supplied instant. */
    CompletionStage<MarketPage> browseAsync(MarketQuery query, Instant now);

    /** Cancels a seller-owned active listing atomically. */
    CompletionStage<MarketListing> cancelAsync(java.util.UUID sellerId, MarketListingId listingId, Instant now);

    /** Reserves a listing and returns an idempotent pending or already-settled purchase. */
    CompletionStage<MarketPurchaseReservation> reservePurchaseAsync(MarketPurchaseRequest request, Instant now);

    /** Finds a purchase receipt for recovery and observability. */
    CompletionStage<Optional<MarketPurchase>> findPurchaseAsync(MarketPurchaseId purchaseId);

    /** Marks a pending purchase as settled and its listing as sold. */
    CompletionStage<Boolean> markSettledAsync(MarketPurchaseId purchaseId, Instant settledAt);

    /** Releases a pending purchase and returns the listing to its active or expired state. */
    CompletionStage<Boolean> releasePendingAsync(MarketPurchaseId purchaseId, Instant now);

    /** Loads bounded pending purchases for crash recovery. */
    CompletionStage<List<MarketPurchase>> pendingPurchasesAsync(int limit);

    /** Purges terminal records older than the cutoff and deliberately ends their idempotency window. */
    CompletionStage<Void> purgeAsync(Instant before);
}
