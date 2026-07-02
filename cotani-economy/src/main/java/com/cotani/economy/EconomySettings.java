package com.cotani.economy;

import com.cotani.economy.currency.EconomyCurrency;
import java.math.BigDecimal;
import java.util.Objects;

public record EconomySettings(
        EconomyCurrency defaultCurrency,
        BigDecimal startingBalance,
        BigDecimal maximumBalance,
        BigDecimal maximumOperationAmount) {

    public EconomySettings {
        Objects.requireNonNull(defaultCurrency, "defaultCurrency");
        Objects.requireNonNull(startingBalance, "startingBalance");
        Objects.requireNonNull(maximumBalance, "maximumBalance");
        Objects.requireNonNull(maximumOperationAmount, "maximumOperationAmount");

        if (startingBalance.signum() < 0) {
            throw new IllegalArgumentException("startingBalance cannot be negative.");
        }

        if (maximumBalance.signum() <= 0) {
            throw new IllegalArgumentException("maximumBalance must be positive.");
        }

        if (maximumOperationAmount.signum() <= 0) {
            throw new IllegalArgumentException("maximumOperationAmount must be positive.");
        }

        if (startingBalance.compareTo(maximumBalance) > 0) {
            throw new IllegalArgumentException("startingBalance cannot be greater than maximumBalance.");
        }
    }

    public static EconomySettings defaultSettings(EconomyCurrency currency) {
        Objects.requireNonNull(currency, "currency");

        return new EconomySettings(
                currency,
                BigDecimal.ZERO.setScale(currency.decimalPlaces()),
                new BigDecimal("1000000000000").setScale(currency.decimalPlaces()),
                new BigDecimal("100000000").setScale(currency.decimalPlaces()));
    }
}
