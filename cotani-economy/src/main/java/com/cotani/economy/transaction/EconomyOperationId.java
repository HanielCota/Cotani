package com.cotani.economy.transaction;

import java.util.Objects;
import java.util.UUID;

public record EconomyOperationId(UUID value) {

    public EconomyOperationId {
        Objects.requireNonNull(value, "value");
    }

    public static EconomyOperationId random() {
        return new EconomyOperationId(UUID.randomUUID());
    }

    public static EconomyOperationId of(UUID value) {
        return new EconomyOperationId(value);
    }
}
