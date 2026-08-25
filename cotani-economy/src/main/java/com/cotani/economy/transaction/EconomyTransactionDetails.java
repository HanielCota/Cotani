package com.cotani.economy.transaction;

import com.cotani.economy.currency.CurrencyId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Immutable details shared by every economy transaction variant. */
public record EconomyTransactionDetails(
        EconomyOperationId operationId,
        CurrencyId currencyId,
        BigDecimal amount,
        EconomyReason reason,
        Instant createdAt) {
    public EconomyTransactionDetails {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(currencyId, "currencyId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
