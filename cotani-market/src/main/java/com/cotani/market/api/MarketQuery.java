package com.cotani.market.api;

import com.cotani.economy.currency.CurrencyId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Bounded query for active listings, ordered newest first. */
public record MarketQuery(
        int page, int pageSize, Optional<String> itemKey, Optional<CurrencyId> currency, Optional<UUID> sellerId) {
    private static final int DEFAULT_PAGE_SIZE = 20;

    public MarketQuery {
        Objects.requireNonNull(itemKey, "itemKey");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(sellerId, "sellerId");
        if (page < 0 || pageSize <= 0) {
            throw new IllegalArgumentException("page must be non-negative and pageSize must be positive");
        }
        itemKey = itemKey.map(MarketItem::normalizeKey);
    }

    public static MarketQuery firstPage(int pageSize) {
        return new MarketQuery(0, pageSize, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int page;
        private int pageSize = DEFAULT_PAGE_SIZE;
        private @Nullable String itemKey;
        private @Nullable CurrencyId currency;
        private @Nullable UUID sellerId;

        public Builder page(int value) {
            this.page = value;
            return this;
        }

        public Builder pageSize(int value) {
            this.pageSize = value;
            return this;
        }

        public Builder itemKey(String value) {
            this.itemKey = Objects.requireNonNull(value, "itemKey");
            return this;
        }

        public Builder currency(CurrencyId value) {
            this.currency = Objects.requireNonNull(value, "currency");
            return this;
        }

        public Builder sellerId(UUID value) {
            this.sellerId = Objects.requireNonNull(value, "sellerId");
            return this;
        }

        public MarketQuery build() {
            return new MarketQuery(
                    page,
                    pageSize,
                    Optional.ofNullable(itemKey),
                    Optional.ofNullable(currency),
                    Optional.ofNullable(sellerId));
        }
    }
}
