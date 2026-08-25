package com.cotani.party.api.event;

import com.cotani.party.api.Party;
import java.util.Objects;
import java.util.UUID;

/** Published after an invitation is accepted. */
public record PartyMemberJoinedEvent(Party party, UUID memberId) implements PartyEvent {
    public PartyMemberJoinedEvent {
        Objects.requireNonNull(party, "party");
        Objects.requireNonNull(memberId, "memberId");
    }
}
