package com.cotani.market.api.event;

import com.cotani.market.api.MarketListing;
import java.util.Objects;

/** Published after a listing is durably cancelled. */
public record ListingCancelledEvent(MarketListing listing) implements MarketEvent {
    public ListingCancelledEvent {
        Objects.requireNonNull(listing, "listing");
    }
}
