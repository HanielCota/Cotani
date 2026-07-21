package com.cotani.economy.internal.cache;

import com.cotani.economy.EconomyService;
import com.cotani.economy.EconomySettings;
import com.cotani.economy.account.EconomyBalance;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * Cache decorador para {@link EconomyService}.
 *
 * <p>Apenas leituras de saldo (<code>balance</code> e <code>has</code>) são cacheadas. Operações de escrita
 * invalidam a entrada do usuário após o storage confirmar sucesso. A implementação usa Caffeine para
 * coalescer requisições concorrentes ao mesmo par (usuário, moeda) e limitar o tamanho do cache.
 *
 * <p>O carregem assíncrono do Caffeine usa o {@link Executor} explícito recebido no construtor, em vez do
 * {@code ForkJoinPool.commonPool()} padrão, para manter o isolamento dos pools do plugin.
 */
public final class CachedEconomyService implements EconomyService, AutoCloseable {

    private static final long MAXIMUM_CACHE_SIZE = 10_000;

    private final EconomyService delegate;
    private final EconomySettings settings;
    private final AsyncLoadingCache<BalanceKey, EconomyBalance> cache;

    public CachedEconomyService(EconomyService delegate, Executor executor, EconomySettings settings) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(executor, "executor");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAXIMUM_CACHE_SIZE)
                .expireAfterWrite(Duration.ofSeconds(settings.balanceCacheSeconds()))
                .executor(executor)
                .buildAsync((key, ignored) -> loadBalance(key));
    }

    @Override
    public CompletionStage<EconomyBalance> balance(UUID userId) {
        return balance(userId, defaultCurrency());
    }

    @Override
    public CompletionStage<EconomyBalance> balance(UUID userId, CurrencyId currencyId) {
        return cache.get(new BalanceKey(userId, currencyId));
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
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        return mutate(userId, delegate.deposit(userId, currencyId, amount, reason, operationId));
    }

    @Override
    public CompletionStage<EconomyTransaction> deposit(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId) {
        return deposit(userId, defaultCurrency(), amount, reason, operationId);
    }

    @Override
    public CompletionStage<EconomyTransaction> withdraw(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        return mutate(userId, delegate.withdraw(userId, currencyId, amount, reason, operationId));
    }

    @Override
    public CompletionStage<EconomyTransaction> withdraw(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId) {
        return withdraw(userId, defaultCurrency(), amount, reason, operationId);
    }

    @Override
    public CompletionStage<EconomyTransaction> set(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        return mutate(userId, delegate.set(userId, currencyId, amount, reason, operationId));
    }

    @Override
    public CompletionStage<EconomyTransaction> set(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId) {
        return set(userId, defaultCurrency(), amount, reason, operationId);
    }

    @Override
    public CompletionStage<EconomyTransaction> transfer(
            UUID sourceUserId,
            UUID targetUserId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        return delegate.transfer(sourceUserId, targetUserId, currencyId, amount, reason, operationId)
                .thenApply(transaction -> {
                    invalidate(sourceUserId, currencyId);
                    invalidate(targetUserId, currencyId);
                    return transaction;
                });
    }

    @Override
    public CompletionStage<EconomyTransaction> transfer(
            UUID sourceUserId,
            UUID targetUserId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        return transfer(sourceUserId, targetUserId, defaultCurrency(), amount, reason, operationId);
    }

    @Override
    public void close() {
        cache.asMap().clear();
    }

    private CompletableFuture<EconomyBalance> loadBalance(BalanceKey key) {
        return delegate.balance(key.userId(), key.currencyId()).toCompletableFuture();
    }

    private CompletionStage<EconomyTransaction> mutate(UUID userId, CompletionStage<EconomyTransaction> future) {
        return future.thenApply(transaction -> {
            invalidate(userId, transaction.currencyId());
            return transaction;
        });
    }

    private void invalidate(UUID userId, CurrencyId currencyId) {
        cache.asMap().remove(new BalanceKey(userId, currencyId));
    }

    private CurrencyId defaultCurrency() {
        return settings.defaultCurrency().id();
    }

    private record BalanceKey(UUID userId, CurrencyId currencyId) {}
}
