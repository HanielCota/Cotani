package com.cotani.economy.internal.repository;

import com.cotani.economy.currency.CurrencyId;
import java.util.Objects;
import java.util.UUID;

record EconomyAccountKey(UUID userId, CurrencyId currencyId) implements Comparable<EconomyAccountKey> {

    EconomyAccountKey {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(currencyId, "currencyId");
    }

    @Override
    public int compareTo(EconomyAccountKey other) {
        int userComparison = userId.compareTo(other.userId);
        if (userComparison != 0) {
            return userComparison;
        }
        return currencyId.value().compareTo(other.currencyId.value());
    }
}
