package com.cotani.economy.account;

import com.cotani.economy.currency.CurrencyId;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record EconomyBalance(UUID userId, CurrencyId currencyId, BigDecimal amount) {

    public EconomyBalance {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(currencyId, "currencyId");
        Objects.requireNonNull(amount, "amount");

        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Balance amount cannot be negative.");
        }
    }

    public static EconomyBalance from(EconomyAccount account) {
        Objects.requireNonNull(account, "account");

        return new EconomyBalance(account.userId(), account.currencyId(), account.balance());
    }
}
