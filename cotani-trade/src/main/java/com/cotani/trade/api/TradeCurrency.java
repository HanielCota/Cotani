package com.cotani.trade.api;

import com.cotani.economy.currency.CurrencyId;
import java.math.BigDecimal;
import java.util.Objects;

/** Immutable currency offer delegated to the configured settlement adapter. */
public record TradeCurrency(CurrencyId key, BigDecimal amount) implements TradeAsset {
    public TradeCurrency {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (amount.scale() > 18) {
            throw new IllegalArgumentException("amount scale must not exceed 18");
        }
        amount = amount.stripTrailingZeros();
    }

    @Override
    public String assetType() {
        return "currency";
    }

    @Override
    public String assetKey() {
        return key.value();
    }

    @Override
    public long encodedSizeBytes() {
        return (long) TradeAsset.utf8Size(key.value()) + TradeAsset.utf8Size(amount.toPlainString());
    }
}
