package com.cotani.economy.transaction;

import com.cotani.economy.currency.CurrencyId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record EconomyTransaction(
        EconomyTransactionId id,
        EconomyOperationId operationId,
        EconomyTransactionType type,
        @Nullable UUID sourceUserId,
        @Nullable UUID targetUserId,
        CurrencyId currencyId,
        BigDecimal amount,
        @Nullable BigDecimal sourceBalanceBefore,
        @Nullable BigDecimal sourceBalanceAfter,
        @Nullable BigDecimal targetBalanceBefore,
        @Nullable BigDecimal targetBalanceAfter,
        EconomyReason reason,
        Instant createdAt) {

    public EconomyTransaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(currencyId, "currencyId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(createdAt, "createdAt");

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive.");
        }
    }

    public Optional<UUID> source() {
        return Optional.ofNullable(sourceUserId);
    }

    public Optional<UUID> target() {
        return Optional.ofNullable(targetUserId);
    }

    public static EconomyTransaction deposit(
            EconomyOperationId operationId,
            UUID targetUserId,
            CurrencyId currencyId,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            EconomyReason reason,
            Instant now) {
        return new EconomyTransaction(
                EconomyTransactionId.random(),
                operationId,
                EconomyTransactionType.DEPOSIT,
                null,
                targetUserId,
                currencyId,
                amount,
                null,
                null,
                balanceBefore,
                balanceAfter,
                reason,
                now);
    }

    public static EconomyTransaction withdraw(
            EconomyOperationId operationId,
            UUID sourceUserId,
            CurrencyId currencyId,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            EconomyReason reason,
            Instant now) {
        return new EconomyTransaction(
                EconomyTransactionId.random(),
                operationId,
                EconomyTransactionType.WITHDRAW,
                sourceUserId,
                null,
                currencyId,
                amount,
                balanceBefore,
                balanceAfter,
                null,
                null,
                reason,
                now);
    }

    public static EconomyTransaction set(
            EconomyOperationId operationId,
            UUID targetUserId,
            CurrencyId currencyId,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            EconomyReason reason,
            Instant now) {
        return new EconomyTransaction(
                EconomyTransactionId.random(),
                operationId,
                EconomyTransactionType.SET,
                null,
                targetUserId,
                currencyId,
                amount,
                null,
                null,
                balanceBefore,
                balanceAfter,
                reason,
                now);
    }

    public static EconomyTransaction transfer(
            EconomyOperationId operationId,
            UUID sourceUserId,
            UUID targetUserId,
            CurrencyId currencyId,
            BigDecimal amount,
            BigDecimal sourceBalanceBefore,
            BigDecimal sourceBalanceAfter,
            BigDecimal targetBalanceBefore,
            BigDecimal targetBalanceAfter,
            EconomyReason reason,
            Instant now) {
        return new EconomyTransaction(
                EconomyTransactionId.random(),
                operationId,
                EconomyTransactionType.TRANSFER,
                sourceUserId,
                targetUserId,
                currencyId,
                amount,
                sourceBalanceBefore,
                sourceBalanceAfter,
                targetBalanceBefore,
                targetBalanceAfter,
                reason,
                now);
    }
}
