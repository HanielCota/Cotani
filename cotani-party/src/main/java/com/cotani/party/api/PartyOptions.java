package com.cotani.party.api;

/** Creation options for a party. */
public record PartyOptions(int maxMembers) {
    public PartyOptions {
        if (maxMembers < 2) {
            throw new IllegalArgumentException("maxMembers must be at least 2");
        }
    }

    public static PartyOptions defaults() {
        return new PartyOptions(8);
    }
}
