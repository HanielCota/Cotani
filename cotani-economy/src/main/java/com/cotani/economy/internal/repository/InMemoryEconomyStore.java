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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class InMemoryEconomyStore implements EconomyAccountRepository, EconomyTransferRepository {

    private final Object lock = new Object();
    private final Executor executor;
    private final Clock clock;
    private final EconomySettings settings;
    private final Map<EconomyAccountKey, EconomyAccount> accounts = new HashMap<>();
    private final Map<EconomyOperationId, EconomyTransaction> transactionsByOperation = new HashMap<>();
    private final List<EconomyTransaction> transactions = new ArrayList<>();

    public InMemoryEconomyStore(Executor executor, Clock clock, EconomySettings settings) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public CompletableFuture<EconomyAccount> getOrCreate(UUID userId, CurrencyId currencyId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(currencyId, "currencyId");

        return CompletableFuture.supplyAsync(
                () -> {
                    synchronized (lock) {
                        return getOrCreateLocked(userId, currencyId);
                    }
                },
                executor);
    }

    @Override
    public CompletableFuture<EconomyTransaction> deposit(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        return CompletableFuture.supplyAsync(
                () -> {
                    synchronized (lock) {
                        assertOperationIsNew(operationId);

                        var now = clock.instant();
                        var account = getOrCreateLocked(userId, currencyId);
                        var updatedAccount = account.deposit(amount, now);
                        ensureMaximumBalance(updatedAccount);

                        accounts.put(new EconomyAccountKey(userId, currencyId), updatedAccount);

                        var transaction = EconomyTransaction.deposit(
                                operationId,
                                userId,
                                currencyId,
                                amount,
                                account.balance(),
                                updatedAccount.balance(),
                                reason,
                                now);

                        saveTransactionLocked(transaction);
                        return transaction;
                    }
                },
                executor);
    }

    @Override
    public CompletableFuture<EconomyTransaction> withdraw(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        return CompletableFuture.supplyAsync(
                () -> {
                    synchronized (lock) {
                        assertOperationIsNew(operationId);

                        var now = clock.instant();
                        var account = getOrCreateLocked(userId, currencyId);
                        var updatedAccount = account.withdraw(amount, now);

                        accounts.put(new EconomyAccountKey(userId, currencyId), updatedAccount);

                        var transaction = EconomyTransaction.withdraw(
                                operationId,
                                userId,
                                currencyId,
                                amount,
                                account.balance(),
                                updatedAccount.balance(),
                                reason,
                                now);

                        saveTransactionLocked(transaction);
                        return transaction;
                    }
                },
                executor);
    }

    @Override
    public CompletableFuture<EconomyTransaction> set(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        return CompletableFuture.supplyAsync(
                () -> {
                    synchronized (lock) {
                        assertOperationIsNew(operationId);

                        var now = clock.instant();
                        var account = getOrCreateLocked(userId, currencyId);
                        var updatedAccount = account.setBalance(amount, now);
                        ensureMaximumBalance(updatedAccount);

                        accounts.put(new EconomyAccountKey(userId, currencyId), updatedAccount);

                        var transaction = EconomyTransaction.set(
                                operationId,
                                userId,
                                currencyId,
                                amount,
                                account.balance(),
                                updatedAccount.balance(),
                                reason,
                                now);

                        saveTransactionLocked(transaction);
                        return transaction;
                    }
                },
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
        return CompletableFuture.supplyAsync(
                () -> {
                    synchronized (lock) {
                        assertOperationIsNew(operationId);

                        var now = clock.instant();
                        var sourceAccount = getOrCreateLocked(sourceUserId, currencyId);
                        var targetAccount = getOrCreateLocked(targetUserId, currencyId);
                        var updatedSource = sourceAccount.withdraw(amount, now);
                        var updatedTarget = targetAccount.deposit(amount, now);
                        ensureMaximumBalance(updatedTarget);

                        accounts.put(new EconomyAccountKey(sourceUserId, currencyId), updatedSource);
                        accounts.put(new EconomyAccountKey(targetUserId, currencyId), updatedTarget);

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
                    }
                },
                executor);
    }

    public CompletableFuture<List<EconomyTransaction>> recentTransactions(int limit) {
        return CompletableFuture.supplyAsync(
                () -> {
                    synchronized (lock) {
                        return transactions.stream()
                                .sorted(Comparator.comparing(EconomyTransaction::createdAt)
                                        .reversed())
                                .limit(limit)
                                .toList();
                    }
                },
                executor);
    }

    private EconomyAccount getOrCreateLocked(UUID userId, CurrencyId currencyId) {
        var key = new EconomyAccountKey(userId, currencyId);
        var existing = accounts.get(key);

        if (existing != null) {
            return existing;
        }

        var now = clock.instant();
        var created = EconomyAccount.create(userId, currencyId, settings.startingBalance(), now);
        accounts.put(key, created);

        return created;
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
}
