package com.cotani.trade.api;

import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable serialized item offer that contains no live Bukkit object. */
public record TradeItem(String key, int amount, String serializedData) implements TradeAsset {
    private static final Pattern ALLOWED_KEY = Pattern.compile("[a-z0-9][a-z0-9._:-]{0,95}");
    private static final int MAX_SERIALIZED_DATA_LENGTH = 1_048_576;

    public TradeItem {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(serializedData, "serializedData");
        key = key.trim().toLowerCase(java.util.Locale.ROOT);
        if (!ALLOWED_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("item key contains unsupported characters");
        }
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (serializedData.length() > MAX_SERIALIZED_DATA_LENGTH) {
            throw new IllegalArgumentException("serializedData is too large");
        }
    }

    @Override
    public String assetType() {
        return "item";
    }

    @Override
    public String assetKey() {
        return key;
    }

    @Override
    public long encodedSizeBytes() {
        return (long) TradeAsset.utf8Size(key) + TradeAsset.utf8Size(serializedData);
    }
}
