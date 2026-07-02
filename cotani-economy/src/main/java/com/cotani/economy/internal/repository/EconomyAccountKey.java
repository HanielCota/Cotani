package com.cotani.economy.internal.repository;

import com.cotani.economy.currency.CurrencyId;
import java.util.Objects;
import java.util.UUID;

record EconomyAccountKey(UUID userId, CurrencyId currencyId) {

    EconomyAccountKey {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(currencyId, "currencyId");
    }
}
