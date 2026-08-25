package com.cotani.trade.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable offer belonging to one participant. */
public record TradeOffer(UUID ownerId, List<TradeAsset> assets) {
    public TradeOffer {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(assets, "assets");
        var copiedAssets = List.copyOf(assets);
        var uniqueAssets = new HashSet<TradeAsset>();
        var currencyKeys = new HashSet<String>();
        copiedAssets.forEach(asset -> {
            if (!uniqueAssets.add(asset)) {
                throw new IllegalArgumentException("an offer cannot contain duplicate assets");
            }
            if (asset instanceof TradeCurrency && !currencyKeys.add(asset.assetKey())) {
                throw new IllegalArgumentException("an offer cannot contain duplicate currencies");
            }
        });
        assets = copiedAssets;
    }

    public static TradeOffer empty(UUID ownerId) {
        return new TradeOffer(ownerId, List.of());
    }

    public long encodedSizeBytes() {
        return assets.stream().mapToLong(TradeAsset::encodedSizeBytes).sum();
    }
}
