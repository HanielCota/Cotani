package com.cotani.party.api.event;

import com.cotani.party.api.Party;
import java.util.Objects;
import java.util.UUID;

/** Published after a member leaves a party. */
public record PartyMemberLeftEvent(Party party, UUID memberId) implements PartyEvent {
    public PartyMemberLeftEvent {
        Objects.requireNonNull(party, "party");
        Objects.requireNonNull(memberId, "memberId");
    }
}
