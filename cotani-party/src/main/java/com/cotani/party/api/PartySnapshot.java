package com.cotani.party.api;

import java.util.List;
import java.util.Objects;

/** Immutable state loaded by a party repository. */
public record PartySnapshot(List<Party> parties) {
    public PartySnapshot {
        Objects.requireNonNull(parties, "parties");
        parties.forEach(party -> Objects.requireNonNull(party, "party"));
        parties = List.copyOf(parties);
    }

    public static PartySnapshot empty() {
        return new PartySnapshot(List.of());
    }
}
