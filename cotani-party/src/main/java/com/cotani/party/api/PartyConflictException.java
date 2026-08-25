package com.cotani.party.api;

/** Raised when persistence detects a conflicting party revision. */
public final class PartyConflictException extends PartyException {
    private static final long serialVersionUID = 1L;

    public PartyConflictException(PartyId partyId) {
        super("Party revision conflict: " + partyId.value());
    }
}
