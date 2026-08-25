package com.cotani.party.api;

import java.util.UUID;

/** Raised when an invitation cannot be created or consumed. */
public final class PartyInviteException extends PartyException {
    private static final long serialVersionUID = 1L;

    public PartyInviteException(UUID playerId, PartyId partyId, String message) {
        super("Player " + playerId + " and party " + partyId.value() + ": " + message);
    }
}
