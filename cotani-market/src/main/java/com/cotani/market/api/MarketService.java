package com.cotani.market.api;

import com.cotani.AsyncCloseable;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Asynchronous marketplace use cases with idempotent settlement recovery. */
public interface MarketService extends AsyncCloseable, AutoCloseable {
    /** Creates a listing using the request's stable id and timestamp. */
    CompletionStage<MarketListing> listAsync(MarketListingRequest request);

    /** Creates a fresh listing request using the service clock. */
    CompletionStage<MarketListing> listAsync(UUID sellerId, MarketItem item, MarketPrice price, Duration duration);

    /** Browses active listings with bounded pagination. */
    CompletionStage<MarketPage> browseAsync(MarketQuery query);

    /** Finds a listing including terminal history. */
    CompletionStage<Optional<MarketListing>> findAsync(MarketListingId listingId);

    /** Cancels an active listing owned by the seller. */
    CompletionStage<MarketListing> cancelAsync(UUID sellerId, MarketListingId listingId);

    /** Reserves, settles and returns a purchase; retries must reuse its purchase id. */
    CompletionStage<MarketPurchase> purchaseAsync(MarketPurchaseRequest request);

    /** Reconciles a pending purchase and releases it only when the adapter proves no effects exist. */
    CompletionStage<MarketPurchase> releasePendingAsync(MarketPurchaseId purchaseId);

    /** Finds a purchase receipt for recovery. */
    CompletionStage<Optional<MarketPurchase>> findPurchaseAsync(MarketPurchaseId purchaseId);

    /** Returns bounded pending purchases that may be retried with the same id. */
    CompletionStage<List<MarketPurchase>> pendingPurchasesAsync(int limit);

    /** Purges terminal records older than the cutoff. */
    CompletionStage<Void> purgeAsync(java.time.Instant before);

    /** Rejects new operations and waits asynchronously for accepted operations to finish. */
    @Override
    CompletionStage<Void> closeAsync();

    @Override
    void close();
}
