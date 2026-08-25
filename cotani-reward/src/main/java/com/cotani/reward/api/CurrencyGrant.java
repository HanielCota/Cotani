package com.cotani.reward.api;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/** A currency amount to be delivered by a host-owned settlement adapter. */
public record CurrencyGrant(String currency, BigDecimal amount) implements RewardGrant {
    public CurrencyGrant {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(amount, "amount");
        currency = currency.strip().toLowerCase(Locale.ROOT);
        if (!currency.matches("[a-z0-9][a-z0-9._-]{0,31}")) {
            throw new IllegalArgumentException("currency must match [a-z0-9][a-z0-9._-]{0,31}");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        amount = amount.stripTrailingZeros();
    }
}
