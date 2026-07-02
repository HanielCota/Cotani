package com.cotani.economy.currency;

import java.util.Objects;

public record EconomyCurrency(CurrencyId id, String name, String symbol, int decimalPlaces) {

    public EconomyCurrency {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(symbol, "symbol");

        name = name.trim();
        symbol = symbol.trim();

        if (name.isBlank()) {
            throw new IllegalArgumentException("Currency name cannot be blank.");
        }

        if (symbol.isBlank()) {
            throw new IllegalArgumentException("Currency symbol cannot be blank.");
        }

        if (decimalPlaces < 0 || decimalPlaces > 8) {
            throw new IllegalArgumentException("Currency decimal places must be between 0 and 8.");
        }
    }

    public static EconomyCurrency coins() {
        return new EconomyCurrency(CurrencyId.of("coins"), "Coins", "$", 2);
    }
}
