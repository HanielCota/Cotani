package com.cotani.party.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable invitation to join a party. */
public record PartyInvite(PartyId partyId, UUID inviterId, UUID inviteeId, Instant expiresAt) {
    public PartyInvite {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(inviterId, "inviterId");
        Objects.requireNonNull(inviteeId, "inviteeId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (inviterId.equals(inviteeId)) {
            throw new IllegalArgumentException("inviterId and inviteeId must be different");
        }
    }

    public boolean isExpiredAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return !instant.isBefore(expiresAt);
    }
}
