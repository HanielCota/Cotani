package com.cotani.economy.internal.cache;

import com.cotani.economy.EconomyService;
import com.cotani.economy.EconomySettings;
import com.cotani.economy.account.EconomyBalance;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import com.cotani.task.api.PaperTaskScheduler;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache decorador para {@link EconomyService}.
 *
 * <p>Apenas leituras de saldo (<code>balance</code> e <code<has</code>) são cacheadas. Operações de escrita
 * invalidam a entrada do usuário após o storage confirmar sucesso.
 */
public final class CachedEconomyService implements EconomyService, AutoCloseable {

    private final EconomyService delegate;
    private final Duration balanceTtl;
    private final ConcurrentHashMap<BalanceKey, CacheEntry> cache = new ConcurrentHashMap<>();

    public CachedEconomyService(EconomyService delegate, PaperTaskScheduler scheduler, EconomySettings settings) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(scheduler, "scheduler");
        this.balanceTtl = Duration.ofSeconds(settings.balanceCacheSeconds());
    }

    @Override
    public CompletionStage<EconomyBalance> balance(UUID userId) {
        return balance(userId, defaultCurrency());
    }

    @Override
    public CompletionStage<EconomyBalance> balance(UUID userId, CurrencyId currencyId) {
        BalanceKey key = new BalanceKey(userId, currencyId);
        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.expired()) {
            return CompletableFuture.completedStage(entry.balance());
        }

        return delegate.balance(userId, currencyId).thenApply(balance -> {
            cache.put(key, new CacheEntry(balance, now(), balanceTtl.toMillis()));
            return balance;
        });
    }

    @Override
    public CompletionStage<Boolean> has(UUID userId, BigDecimal amount) {
        return has(userId, defaultCurrency(), amount);
    }

    @Override
    public CompletionStage<Boolean> has(UUID userId, CurrencyId currencyId, BigDecimal amount) {
        return balance(userId, currencyId).thenApply(balance -> balance.amount().compareTo(amount) >= 0);
    }

    @Override
    public CompletionStage<EconomyTransaction> deposit(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId) {
        return mutate(userId, delegate.deposit(userId, amount, reason, operationId));
    }

    @Override
    public CompletionStage<EconomyTransaction> withdraw(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId) {
        return mutate(userId, delegate.withdraw(userId, amount, reason, operationId));
    }

    @Override
    public CompletionStage<EconomyTransaction> set(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId) {
        return mutate(userId, delegate.set(userId, amount, reason, operationId));
    }

    @Override
    public CompletionStage<EconomyTransaction> transfer(
            UUID sourceUserId,
            UUID targetUserId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        return delegate.transfer(sourceUserId, targetUserId, amount, reason, operationId)
                .thenApply(transaction -> {
                    invalidate(sourceUserId);
                    invalidate(targetUserId);
                    return transaction;
                });
    }

    @Override
    public void close() {
        cache.clear();
    }

    private CompletionStage<EconomyTransaction> mutate(UUID userId, CompletionStage<EconomyTransaction> stage) {
        return stage.thenApply(transaction -> {
            invalidate(userId);
            return transaction;
        });
    }

    private void invalidate(UUID userId) {
        cache.keySet().removeIf(key -> key.userId().equals(userId));
    }

    private CurrencyId defaultCurrency() {
        return com.cotani.economy.currency.CurrencyId.of("coins");
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private record BalanceKey(UUID userId, CurrencyId currencyId) {}

    private record CacheEntry(EconomyBalance balance, long cachedAt, long ttlMillis) {
        boolean expired() {
            return System.currentTimeMillis() - cachedAt > ttlMillis;
        }
    }
}
