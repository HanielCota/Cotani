package com.cotani.party.api;

/** Base exception for expected party-domain failures. */
public class PartyException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PartyException(String message) {
        super(message);
    }
}
