package com.cotani.party.api.event;

import com.cotani.party.api.Party;
import java.util.Objects;
import java.util.UUID;

/** Published after leadership is transferred. */
public record PartyLeadershipTransferredEvent(Party party, UUID previousLeaderId, UUID newLeaderId)
        implements PartyEvent {
    public PartyLeadershipTransferredEvent {
        Objects.requireNonNull(party, "party");
        Objects.requireNonNull(previousLeaderId, "previousLeaderId");
        Objects.requireNonNull(newLeaderId, "newLeaderId");
    }
}
