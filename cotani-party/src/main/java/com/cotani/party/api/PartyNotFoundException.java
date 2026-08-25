package com.cotani.party.api;

/** Raised when a requested party does not exist. */
public final class PartyNotFoundException extends PartyException {
    private static final long serialVersionUID = 1L;

    public PartyNotFoundException(PartyId partyId) {
        super("Party not found: " + partyId.value());
    }
}
