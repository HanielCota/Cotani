package com.cotani.market.api;

import com.cotani.economy.currency.CurrencyId;
import java.math.BigDecimal;
import java.util.Objects;

/** Immutable positive price expressed in one Cotani economy currency. */
public record MarketPrice(CurrencyId currency, BigDecimal amount) {
    public MarketPrice {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0 || amount.scale() > 18) {
            throw new IllegalArgumentException("amount must be positive with scale at most 18");
        }
        amount = amount.stripTrailingZeros();
    }
}
