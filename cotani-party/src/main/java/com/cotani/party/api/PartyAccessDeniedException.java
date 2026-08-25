package com.cotani.party.api;

import java.util.UUID;

/** Raised when a player cannot perform an administrative party operation. */
public final class PartyAccessDeniedException extends PartyException {
    private static final long serialVersionUID = 1L;

    public PartyAccessDeniedException(UUID playerId, PartyId partyId) {
        super("Player " + playerId + " cannot perform this operation for party " + partyId.value());
    }
}
