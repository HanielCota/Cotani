package com.cotani.party.api.event;

import com.cotani.party.api.Party;
import java.util.Objects;
import java.util.UUID;

/** Published after a member is removed by an authorized party member. */
public record PartyMemberKickedEvent(Party party, UUID actorId, UUID memberId) implements PartyEvent {
    public PartyMemberKickedEvent {
        Objects.requireNonNull(party, "party");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(memberId, "memberId");
    }
}
