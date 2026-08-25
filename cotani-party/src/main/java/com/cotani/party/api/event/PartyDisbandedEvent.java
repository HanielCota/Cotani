package com.cotani.party.api.event;

import com.cotani.party.api.PartyId;
import java.util.Objects;
import java.util.UUID;

/** Published after a party is deleted. */
public record PartyDisbandedEvent(PartyId partyId, UUID actorId) implements PartyEvent {
    public PartyDisbandedEvent {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(actorId, "actorId");
    }
}
