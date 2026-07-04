package com.cotani.economy.account;

import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.exception.InsufficientFundsException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EconomyAccount(
        UUID userId, CurrencyId currencyId, BigDecimal balance, Instant createdAt, Instant updatedAt) {

    public EconomyAccount {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(currencyId, "currencyId");
        Objects.requireNonNull(balance, "balance");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");

        if (balance.signum() < 0) {
            throw new IllegalArgumentException("Account balance cannot be negative.");
        }
    }

    public static EconomyAccount create(UUID userId, CurrencyId currencyId, BigDecimal startingBalance, Instant now) {
        return new EconomyAccount(userId, currencyId, startingBalance, now, now);
    }

    private static void validatePositive(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }
    }

    public EconomyAccount deposit(BigDecimal amount, Instant now) {
        validatePositive(amount);

        return withBalance(balance.add(amount), now);
    }

    public EconomyAccount withdraw(BigDecimal amount, Instant now) {
        validatePositive(amount);

        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(userId, balance, amount);
        }

        return withBalance(balance.subtract(amount), now);
    }

    public EconomyAccount setBalance(BigDecimal amount, Instant now) {
        Objects.requireNonNull(amount, "amount");

        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Account balance cannot be negative.");
        }

        return withBalance(amount, now);
    }

    private EconomyAccount withBalance(BigDecimal amount, Instant now) {
        Objects.requireNonNull(now, "now");

        return new EconomyAccount(userId, currencyId, amount, createdAt, now);
    }
}
