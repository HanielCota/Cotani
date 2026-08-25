package com.cotani.market.api;

import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable serialized item snapshot; it never contains a live Bukkit ItemStack. */
public record MarketItem(String key, int amount, String serializedData) {
    private static final Pattern ALLOWED_KEY = Pattern.compile("[a-z0-9][a-z0-9._:-]{0,95}");
    public static final int MAX_SERIALIZED_DATA_LENGTH = 1_048_576;

    public MarketItem {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(serializedData, "serializedData");
        key = normalizeKey(key);
        if (amount <= 0 || amount > 64) {
            throw new IllegalArgumentException("amount must be between 1 and 64");
        }
        if (serializedData.isBlank() || serializedData.length() > MAX_SERIALIZED_DATA_LENGTH) {
            throw new IllegalArgumentException("serializedData must be non-blank and at most 1048576 characters");
        }
    }

    static String normalizeKey(String key) {
        var normalized = Objects.requireNonNull(key, "key").strip().toLowerCase(java.util.Locale.ROOT);
        if (!ALLOWED_KEY.matcher(normalized).matches()) {
            throw new IllegalArgumentException("item key contains unsupported characters");
        }
        return normalized;
    }
}
