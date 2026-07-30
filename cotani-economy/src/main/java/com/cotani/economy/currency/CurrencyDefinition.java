package com.cotani.economy.currency;

import java.math.BigDecimal;
import java.util.Objects;

/** Operational limits and initial state for one economy currency. */
public record CurrencyDefinition(
        EconomyCurrency currency,
        BigDecimal startingBalance,
        BigDecimal maximumBalance,
        BigDecimal maximumOperationAmount,
        BigDecimal minimumPayAmount,
        boolean enabled) {

    public CurrencyDefinition {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(startingBalance, "startingBalance");
        Objects.requireNonNull(maximumBalance, "maximumBalance");
        Objects.requireNonNull(maximumOperationAmount, "maximumOperationAmount");
        Objects.requireNonNull(minimumPayAmount, "minimumPayAmount");
        if (startingBalance.signum() < 0) {
            throw new IllegalArgumentException("startingBalance cannot be negative.");
        }
        if (maximumBalance.signum() <= 0) {
            throw new IllegalArgumentException("maximumBalance must be positive.");
        }
        if (maximumOperationAmount.signum() <= 0 || maximumOperationAmount.compareTo(maximumBalance) > 0) {
            throw new IllegalArgumentException("maximumOperationAmount must be positive and at most maximumBalance.");
        }
        if (minimumPayAmount.signum() < 0 || minimumPayAmount.compareTo(maximumOperationAmount) > 0) {
            throw new IllegalArgumentException(
                    "minimumPayAmount must be non-negative and at most maximumOperationAmount.");
        }
        if (startingBalance.compareTo(maximumBalance) > 0) {
            throw new IllegalArgumentException("startingBalance cannot be greater than maximumBalance.");
        }
    }
}
