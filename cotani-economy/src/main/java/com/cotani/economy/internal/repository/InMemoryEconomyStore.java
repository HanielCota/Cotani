package com.cotani.economy.internal.repository;

import com.cotani.economy.EconomySettings;
import com.cotani.economy.account.EconomyAccount;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.exception.DuplicateEconomyOperationException;
import com.cotani.economy.exception.MaximumBalanceExceededException;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * In-memory economy store backed by {@link ConcurrentHashMap}.
 *
 * <p>Each account is protected by its own lock to allow concurrent operations on different accounts.
 * Transfers lock both source and target accounts in a deterministic key order to avoid deadlocks.
 */
public final class InMemoryEconomyStore implements EconomyAccountRepository, EconomyTransferRepository {

    private final Executor executor;
    private final Clock clock;
    private final EconomySettings settings;
    private final ConcurrentMap<EconomyAccountKey, EconomyAccount> accounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<EconomyOperationId, EconomyTransaction> transactionsByOperation =
            new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<EconomyTransaction> transactions = new ConcurrentLinkedDeque<>();
    private final ConcurrentMap<EconomyAccountKey, Object> locks = new ConcurrentHashMap<>();

    public InMemoryEconomyStore(Executor executor, Clock clock, EconomySettings settings) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public CompletableFuture<EconomyAccount> getOrCreate(UUID userId, CurrencyId currencyId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(currencyId, "currencyId");

        var key = new EconomyAccountKey(userId, currencyId);
        return CompletableFuture.supplyAsync(() -> getOrCreateLocked(key), executor);
    }

    @Override
    public CompletableFuture<EconomyTransaction> deposit(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {

        var key = new EconomyAccountKey(userId, currencyId);
        return CompletableFuture.supplyAsync(
                () -> withLock(key, () -> {
                    assertOperationIsNew(operationId);

                    var now = clock.instant();
                    var account = getOrCreateLocked(key);
                    var updatedAccount = account.deposit(amount, now);
                    ensureMaximumBalance(updatedAccount);

                    accounts.put(key, updatedAccount);

                    var transaction = EconomyTransaction.deposit(
                            operationId,
                            key.userId(),
                            key.currencyId(),
                            amount,
                            account.balance(),
                            updatedAccount.balance(),
                            reason,
                            now);

                    saveTransactionLocked(transaction);
                    return transaction;
                }),
                executor);
    }

    @Override
    public CompletableFuture<EconomyTransaction> withdraw(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        var key = new EconomyAccountKey(userId, currencyId);
        return CompletableFuture.supplyAsync(
                () -> withLock(key, () -> {
                    assertOperationIsNew(operationId);

                    var now = clock.instant();
                    var account = getOrCreateLocked(key);
                    var updatedAccount = account.withdraw(amount, now);

                    accounts.put(key, updatedAccount);

                    var transaction = EconomyTransaction.withdraw(
                            operationId,
                            key.userId(),
                            key.currencyId(),
                            amount,
                            account.balance(),
                            updatedAccount.balance(),
                            reason,
                            now);

                    saveTransactionLocked(transaction);
                    return transaction;
                }),
                executor);
    }

    @Override
    public CompletableFuture<EconomyTransaction> set(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        var key = new EconomyAccountKey(userId, currencyId);
        return CompletableFuture.supplyAsync(
                () -> withLock(key, () -> {
                    assertOperationIsNew(operationId);

                    var now = clock.instant();
                    var account = getOrCreateLocked(key);
                    var updatedAccount = account.setBalance(amount, now);
                    ensureMaximumBalance(updatedAccount);

                    accounts.put(key, updatedAccount);

                    var transaction = EconomyTransaction.set(
                            operationId,
                            key.userId(),
                            key.currencyId(),
                            amount,
                            account.balance(),
                            updatedAccount.balance(),
                            reason,
                            now);

                    saveTransactionLocked(transaction);
                    return transaction;
                }),
                executor);
    }

    @Override
    public CompletableFuture<EconomyTransaction> transfer(
            UUID sourceUserId,
            UUID targetUserId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        var sourceKey = new EconomyAccountKey(sourceUserId, currencyId);
        var targetKey = new EconomyAccountKey(targetUserId, currencyId);

        return CompletableFuture.supplyAsync(
                () -> withBothLocks(sourceKey, targetKey, () -> {
                    assertOperationIsNew(operationId);

                    var now = clock.instant();
                    var sourceAccount = getOrCreateLocked(sourceKey);
                    var targetAccount = getOrCreateLocked(targetKey);
                    var updatedSource = sourceAccount.withdraw(amount, now);
                    var updatedTarget = targetAccount.deposit(amount, now);
                    ensureMaximumBalance(updatedTarget);

                    accounts.put(sourceKey, updatedSource);
                    accounts.put(targetKey, updatedTarget);

                    var transaction = EconomyTransaction.transfer(
                            operationId,
                            sourceUserId,
                            targetUserId,
                            currencyId,
                            amount,
                            sourceAccount.balance(),
                            updatedSource.balance(),
                            targetAccount.balance(),
                            updatedTarget.balance(),
                            reason,
                            now);

                    saveTransactionLocked(transaction);
                    return transaction;
                }),
                executor);
    }

    public CompletableFuture<List<EconomyTransaction>> recentTransactions(int limit) {
        return CompletableFuture.supplyAsync(
                () -> transactions.stream()
                        .sorted(Comparator.comparing(EconomyTransaction::createdAt)
                                .reversed())
                        .limit(limit)
                        .toList(),
                executor);
    }

    private EconomyAccount getOrCreateLocked(EconomyAccountKey key) {
        var existing = accounts.get(key);
        if (existing != null) {
            return existing;
        }

        var now = clock.instant();
        var created = EconomyAccount.create(key.userId(), key.currencyId(), settings.startingBalance(), now);
        var previous = accounts.putIfAbsent(key, created);
        return previous != null ? previous : created;
    }

    private void assertOperationIsNew(EconomyOperationId operationId) {
        Objects.requireNonNull(operationId, "operationId");

        if (transactionsByOperation.containsKey(operationId)) {
            throw new DuplicateEconomyOperationException(operationId);
        }
    }

    private void saveTransactionLocked(EconomyTransaction transaction) {
        transactionsByOperation.put(transaction.operationId(), transaction);
        transactions.add(transaction);
    }

    private void ensureMaximumBalance(EconomyAccount account) {
        if (account.balance().compareTo(settings.maximumBalance()) <= 0) {
            return;
        }

        throw new MaximumBalanceExceededException(account.userId(), account.balance(), settings.maximumBalance());
    }

    private Object lockFor(EconomyAccountKey key) {
        return locks.computeIfAbsent(key, _ -> new Object());
    }

    private <T> T withLock(EconomyAccountKey key, java.util.function.Supplier<T> action) {
        synchronized (lockFor(key)) {
            return action.get();
        }
    }

    private <T> T withBothLocks(
            EconomyAccountKey first, EconomyAccountKey second, java.util.function.Supplier<T> action) {
        if (first.equals(second)) {
            return withLock(first, action);
        }

        var ordered = first.compareTo(second) < 0
                ? new EconomyAccountKey[] {first, second}
                : new EconomyAccountKey[] {second, first};
        synchronized (lockFor(ordered[0])) {
            synchronized (lockFor(ordered[1])) {
                return action.get();
            }
        }
    }
}
