package com.cotani.party.api;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for a party. */
public record PartyId(UUID value) {
    public PartyId {
        Objects.requireNonNull(value, "value");
    }

    public static PartyId random() {
        return new PartyId(UUID.randomUUID());
    }
}
