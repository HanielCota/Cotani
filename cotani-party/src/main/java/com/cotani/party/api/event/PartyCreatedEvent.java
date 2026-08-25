package com.cotani.party.api.event;

import com.cotani.party.api.Party;
import java.util.Objects;

/** Published after a party is persisted and created. */
public record PartyCreatedEvent(Party party) implements PartyEvent {
    public PartyCreatedEvent {
        Objects.requireNonNull(party, "party");
    }
}
