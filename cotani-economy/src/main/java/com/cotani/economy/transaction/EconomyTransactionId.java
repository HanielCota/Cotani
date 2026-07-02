package com.cotani.economy.transaction;

import java.util.Objects;
import java.util.UUID;

public record EconomyTransactionId(UUID value) {

    public EconomyTransactionId {
        Objects.requireNonNull(value, "value");
    }

    public static EconomyTransactionId random() {
        return new EconomyTransactionId(UUID.randomUUID());
    }
}
