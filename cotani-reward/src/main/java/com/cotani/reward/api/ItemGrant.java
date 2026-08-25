package com.cotani.reward.api;

import java.util.Objects;

/** A namespaced item key and quantity to be delivered by a host adapter. */
public record ItemGrant(String itemKey, int amount) implements RewardGrant {
    public ItemGrant {
        Objects.requireNonNull(itemKey, "itemKey");
        itemKey = itemKey.strip();
        if (itemKey.isEmpty() || itemKey.length() > 128) {
            throw new IllegalArgumentException("itemKey must contain between 1 and 128 characters");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
