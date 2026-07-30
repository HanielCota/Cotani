package com.cotani.examples.storage;

import java.util.UUID;

public record ExampleUser(UUID uniqueId, String name, long coins) {

    public ExampleUser addCoins(long amount) {
        return new ExampleUser(uniqueId, name, Math.addExact(coins, amount));
    }
}
