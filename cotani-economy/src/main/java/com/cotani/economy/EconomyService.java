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
 * {@link CompletionStage} that completes with the result once persistence and event publication are
 * done. Callers must compose stages (for example via {@code thenApply}, {@code thenCompose} or
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

    CompletionStage<EconomyBalance> balance(UUID userId, CurrencyId currencyId);

    CompletionStage<Boolean> has(UUID userId, BigDecimal amount);

    CompletionStage<Boolean> has(UUID userId, CurrencyId currencyId, BigDecimal amount);

    CompletionStage<EconomyTransaction> deposit(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId);

    CompletionStage<EconomyTransaction> deposit(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId);

    CompletionStage<EconomyTransaction> withdraw(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId);

    CompletionStage<EconomyTransaction> withdraw(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId);

    CompletionStage<EconomyTransaction> set(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId);

    CompletionStage<EconomyTransaction> set(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId);

    CompletionStage<EconomyTransaction> transfer(
            UUID sourceUserId,
            UUID targetUserId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId);

    CompletionStage<EconomyTransaction> transfer(
            UUID sourceUserId,
            UUID targetUserId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId);
}
