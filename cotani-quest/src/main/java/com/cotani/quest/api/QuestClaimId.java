package com.cotani.quest.api;

import java.util.Objects;
import java.util.UUID;

/** Idempotency key for one logical quest claim. */
public record QuestClaimId(UUID value) {
    public QuestClaimId {
        Objects.requireNonNull(value, "value");
    }

    public static QuestClaimId random() {
        return new QuestClaimId(UUID.randomUUID());
    }
}
