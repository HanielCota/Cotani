package com.cotani.party.api.event;

import com.cotani.party.api.Party;
import com.cotani.party.api.PartyRole;
import java.util.Objects;
import java.util.UUID;

/** Published after a member role changes. */
public record PartyRoleChangedEvent(Party party, UUID actorId, UUID memberId, PartyRole role) implements PartyEvent {
    public PartyRoleChangedEvent {
        Objects.requireNonNull(party, "party");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(memberId, "memberId");
        Objects.requireNonNull(role, "role");
    }
}
