package com.cotani.market.api;

import java.util.List;
import java.util.Objects;

/** Immutable bounded marketplace page. */
public record MarketPage(List<MarketListing> listings, boolean hasMore) {
    public MarketPage {
        listings = List.copyOf(Objects.requireNonNull(listings, "listings"));
        listings.forEach(listing -> Objects.requireNonNull(listing, "listing"));
    }
}
