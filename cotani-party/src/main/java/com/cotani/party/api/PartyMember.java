package com.cotani.party.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable membership entry in a party. */
public record PartyMember(UUID playerId, PartyRole role, Instant joinedAt) {
    public PartyMember {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(joinedAt, "joinedAt");
    }
}
