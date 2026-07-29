package com.cotani.economy.internal;

import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.exception.DuplicateEconomyOperationException;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import com.cotani.economy.transaction.EconomyTransactionType;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Immutable identity of the request associated with an idempotency key. */
@com.cotani.api.InternalApi
public record EconomyOperationFingerprint(
        EconomyTransactionType type,
        @Nullable UUID sourceUserId,
        @Nullable UUID targetUserId,
        CurrencyId currencyId,
        BigDecimal amount,
        EconomyReason reason) {

    public EconomyOperationFingerprint {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(currencyId, "currencyId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(reason, "reason");
    }

    public static EconomyOperationFingerprint deposit(
            UUID userId, CurrencyId currencyId, BigDecimal amount, EconomyReason reason) {
        return new EconomyOperationFingerprint(
                EconomyTransactionType.DEPOSIT, null, userId, currencyId, amount, reason);
    }

    public static EconomyOperationFingerprint withdraw(
            UUID userId, CurrencyId currencyId, BigDecimal amount, EconomyReason reason) {
        return new EconomyOperationFingerprint(
                EconomyTransactionType.WITHDRAW, userId, null, currencyId, amount, reason);
    }

    public static EconomyOperationFingerprint set(
            UUID userId, CurrencyId currencyId, BigDecimal amount, EconomyReason reason) {
        return new EconomyOperationFingerprint(EconomyTransactionType.SET, null, userId, currencyId, amount, reason);
    }

    public static EconomyOperationFingerprint transfer(
            UUID sourceUserId, UUID targetUserId, CurrencyId currencyId, BigDecimal amount, EconomyReason reason) {
        return new EconomyOperationFingerprint(
                EconomyTransactionType.TRANSFER, sourceUserId, targetUserId, currencyId, amount, reason);
    }

    public EconomyTransaction requireMatch(EconomyOperationId operationId, EconomyTransaction existingTransaction) {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(existingTransaction, "existingTransaction");
        if (type == existingTransaction.type()
                && Objects.equals(sourceUserId, existingTransaction.sourceUserId())
                && Objects.equals(targetUserId, existingTransaction.targetUserId())
                && currencyId.equals(existingTransaction.currencyId())
                && amount.compareTo(existingTransaction.amount()) == 0
                && reason.equals(existingTransaction.reason())) {
            return existingTransaction;
        }
        throw new DuplicateEconomyOperationException(operationId);
    }

    public boolean sameRequest(EconomyOperationFingerprint other) {
        Objects.requireNonNull(other, "other");
        return type == other.type
                && Objects.equals(sourceUserId, other.sourceUserId)
                && Objects.equals(targetUserId, other.targetUserId)
                && currencyId.equals(other.currencyId)
                && amount.compareTo(other.amount) == 0
                && reason.equals(other.reason);
    }
}
