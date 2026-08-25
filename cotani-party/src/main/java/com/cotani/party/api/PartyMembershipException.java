package com.cotani.party.api;

import java.util.UUID;

/** Raised when a party membership invariant is violated. */
public final class PartyMembershipException extends PartyException {
    private static final long serialVersionUID = 1L;

    public PartyMembershipException(UUID playerId, String message) {
        super("Player " + playerId + ": " + message);
    }
}
