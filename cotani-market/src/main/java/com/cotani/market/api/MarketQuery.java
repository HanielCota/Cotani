package com.cotani.market.api;

import com.cotani.economy.currency.CurrencyId;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Bounded query for active listings, ordered newest first. */
public record MarketQuery(
        int page,
        int pageSize,
        @Nullable String itemKey,
        @Nullable CurrencyId currency,
        @Nullable UUID sellerId) {
    public MarketQuery {
        if (page < 0 || pageSize <= 0) {
            throw new IllegalArgumentException("page must be non-negative and pageSize must be positive");
        }
        itemKey = itemKey == null ? null : MarketItem.normalizeKey(itemKey);
    }

    public static MarketQuery firstPage(int pageSize) {
        return new MarketQuery(0, pageSize, null, null, null);
    }
}
