package com.cotani.economy.transaction;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Immutable before-and-after balance values for one account. */
public record EconomyBalanceChange(UUID userId, BigDecimal before, BigDecimal after) {
    public EconomyBalanceChange {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
    }
}
