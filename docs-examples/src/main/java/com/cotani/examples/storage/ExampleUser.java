package com.cotani.examples.storage;

import java.util.UUID;

public record ExampleUser(UUID uniqueId, String name, long coins) {
    public ExampleUser {
        java.util.Objects.requireNonNull(uniqueId, "uniqueId");
        java.util.Objects.requireNonNull(name, "name");
        if (coins < 0) {
            throw new IllegalArgumentException("coins must not be negative");
        }
    }

    public ExampleUser addCoins(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return new ExampleUser(uniqueId, name, Math.addExact(coins, amount));
    }
}
