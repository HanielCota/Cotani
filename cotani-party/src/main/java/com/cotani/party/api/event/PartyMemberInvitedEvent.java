package com.cotani.party.api.event;

import com.cotani.party.api.PartyId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Published after an invitation is registered. */
public record PartyMemberInvitedEvent(PartyId partyId, UUID inviterId, UUID inviteeId, Instant expiresAt)
        implements PartyEvent {
    public PartyMemberInvitedEvent {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(inviterId, "inviterId");
        Objects.requireNonNull(inviteeId, "inviteeId");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
