package com.cotani.market.api.event;

import com.cotani.market.api.MarketListing;
import java.util.Objects;

/** Published after a listing is durably created. */
public record ListingCreatedEvent(MarketListing listing) implements MarketEvent {
    public ListingCreatedEvent {
        Objects.requireNonNull(listing, "listing");
    }
}
