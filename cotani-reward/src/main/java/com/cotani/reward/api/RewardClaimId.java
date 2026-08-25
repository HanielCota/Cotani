package com.cotani.reward.api;

import java.util.Objects;
import java.util.UUID;

/** Idempotency key for one logical reward claim attempt. */
public record RewardClaimId(UUID value) {
    public RewardClaimId {
        Objects.requireNonNull(value, "value");
    }

    public static RewardClaimId random() {
        return new RewardClaimId(UUID.randomUUID());
    }
}
