package com.cotani.economy;

import com.cotani.economy.account.EconomyBalance;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Public economy API used by other Cotani modules.
 *
 * <p>All methods are asynchronous and never block the calling thread. They return a
 * {@link CompletionStage} that completes with the result once persistence is durable. Event
 * publication is best effort: a publication failure is logged and a later idempotent invocation may
 * retry it. Callers must compose stages (for example via {@code thenApply}, {@code thenCompose} or
 * {@code whenComplete}) instead of blocking on the result.
 *
 * <p>Domain failures are delivered through the failed stage rather than thrown synchronously:
 * <ul>
 *     <li>{@link com.cotani.economy.exception.InvalidAmountException} for non-positive or unnormalized amounts;</li>
 *     <li>{@link com.cotani.economy.exception.InsufficientFundsException} for withdrawals exceeding the balance;</li>
 *     <li>{@link com.cotani.economy.exception.DuplicateEconomyOperationException} for a reused
 *     {@link EconomyOperationId}.</li>
 * </ul>
 *
 * <p>Every mutating call requires a unique {@link EconomyOperationId} for idempotency. Generate a fresh
 * id (for example {@code EconomyOperationId.random()}) per logical operation and never reuse the same id
 * for different operations, otherwise the call may be rejected as a duplicate.
 */
public interface EconomyService {
    CompletionStage<EconomyBalance> balance(UUID userId);

    default CompletionStage<EconomyBalance> balanceAsync(UUID userId) {
        return balance(userId);
    }

    CompletionStage<EconomyBalance> balance(UUID userId, CurrencyId currencyId);

    default CompletionStage<EconomyBalance> balanceAsync(UUID userId, CurrencyId currencyId) {
        return balance(userId, currencyId);
    }

    CompletionStage<Boolean> has(UUID userId, BigDecimal amount);

    default CompletionStage<Boolean> hasAsync(UUID userId, BigDecimal amount) {
        return has(userId, amount);
    }

    CompletionStage<Boolean> has(UUID userId, CurrencyId currencyId, BigDecimal amount);

    default CompletionStage<Boolean> hasAsync(UUID userId, CurrencyId currencyId, BigDecimal amount) {
        return has(userId, currencyId, amount);
    }

    CompletionStage<EconomyTransaction> deposit(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId);

    default CompletionStage<EconomyTransaction> depositAsync(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        return deposit(userId, currencyId, amount, reason, operationId);
    }

    CompletionStage<EconomyTransaction> deposit(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId);

    default CompletionStage<EconomyTransaction> depositAsync(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId) {
        return deposit(userId, amount, reason, operationId);
    }

    CompletionStage<EconomyTransaction> withdraw(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId);

    default CompletionStage<EconomyTransaction> withdrawAsync(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        return withdraw(userId, currencyId, amount, reason, operationId);
    }

    CompletionStage<EconomyTransaction> withdraw(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId);

    default CompletionStage<EconomyTransaction> withdrawAsync(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId) {
        return withdraw(userId, amount, reason, operationId);
    }

    CompletionStage<EconomyTransaction> set(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId);

    default CompletionStage<EconomyTransaction> setAsync(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        return set(userId, currencyId, amount, reason, operationId);
    }

    CompletionStage<EconomyTransaction> set(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId);

    default CompletionStage<EconomyTransaction> setAsync(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId) {
        return set(userId, amount, reason, operationId);
    }

    CompletionStage<EconomyTransaction> transfer(
            UUID sourceUserId,
            UUID targetUserId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId);

    default CompletionStage<EconomyTransaction> transferAsync(
            UUID sourceUserId,
            UUID targetUserId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        return transfer(sourceUserId, targetUserId, currencyId, amount, reason, operationId);
    }

    CompletionStage<EconomyTransaction> transfer(
            UUID sourceUserId,
            UUID targetUserId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId);

    default CompletionStage<EconomyTransaction> transferAsync(
            UUID sourceUserId,
            UUID targetUserId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        return transfer(sourceUserId, targetUserId, amount, reason, operationId);
    }
}
