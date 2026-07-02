package com.cotani.economy.internal.storage;

import com.cotani.economy.EconomySettings;
import com.cotani.economy.account.EconomyAccount;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.exception.MaximumBalanceExceededException;
import com.cotani.economy.internal.repository.EconomyAccountRepository;
import com.cotani.economy.internal.repository.EconomyTransferRepository;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.executor.QueryExecutor;
import com.cotani.storage.query.Row;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * SQL-backed implementation of economy persistence.
 *
 * <p>Operations run on the storage executor provided by {@link CotaniStorage}. Balance mutations use
 * conditional updates so races cannot produce negative balances or exceed maximum balance.
 */
public final class SqlEconomyStore implements EconomyAccountRepository, EconomyTransferRepository {

    private final CotaniStorage storage;
    private final Clock clock;
    private final EconomySettings settings;

    public SqlEconomyStore(CotaniStorage storage, Clock clock, EconomySettings settings) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public CompletionStage<EconomyAccount> getOrCreate(UUID userId, CurrencyId currencyId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(currencyId, "currencyId");

        return storage.executor().transaction(tx -> {
            EconomyAccount account = getOrCreateLocked(tx, userId, currencyId);
            return java.util.concurrent.CompletableFuture.<EconomyAccount>completedStage(account);
        });
    }

    @Override
    public CompletionStage<EconomyTransaction> deposit(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(currencyId, "currencyId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(operationId, "operationId");

        Instant now = clock.instant();
        return storage.executor().transaction(tx -> {
            EconomyAccount account = getOrCreateLocked(tx, userId, currencyId);
            EconomyAccount updated = account.deposit(amount, now);
            ensureMaximumBalance(updated);
            EconomyTransaction transaction = EconomyTransaction.deposit(
                    operationId, userId, currencyId, amount, account.balance(), updated.balance(), reason, now);
            return insertAccount(tx, updated)
                    .thenCompose(_ -> insertTransaction(tx, transaction))
                    .thenApply(_ -> transaction);
        });
    }

    @Override
    public CompletionStage<EconomyTransaction> withdraw(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(currencyId, "currencyId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(operationId, "operationId");

        Instant now = clock.instant();
        return storage.executor().transaction(tx -> {
            EconomyAccount account = getOrCreateLocked(tx, userId, currencyId);
            EconomyAccount updated = account.withdraw(amount, now);
            EconomyTransaction transaction = EconomyTransaction.withdraw(
                    operationId, userId, currencyId, amount, account.balance(), updated.balance(), reason, now);
            return updateBalanceIfSufficient(tx, userId, currencyId, amount, updated.balance(), now)
                    .thenCompose(success -> success
                            ? insertTransaction(tx, transaction).thenApply(_ -> transaction)
                            : java.util.concurrent.CompletableFuture.<EconomyTransaction>failedFuture(
                                    new com.cotani.economy.exception.InsufficientFundsException(
                                            userId, account.balance(), amount)));
        });
    }

    @Override
    public CompletionStage<EconomyTransaction> set(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(currencyId, "currencyId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(operationId, "operationId");

        Instant now = clock.instant();
        return storage.executor().transaction(tx -> {
            EconomyAccount account = getOrCreateLocked(tx, userId, currencyId);
            EconomyAccount updated = account.setBalance(amount, now);
            ensureMaximumBalance(updated);
            EconomyTransaction transaction = EconomyTransaction.set(
                    operationId, userId, currencyId, amount, account.balance(), updated.balance(), reason, now);
            return upsertAccount(tx, updated)
                    .thenCompose(_ -> insertTransaction(tx, transaction))
                    .thenApply(_ -> transaction);
        });
    }

    @Override
    public CompletionStage<EconomyTransaction> transfer(
            UUID sourceUserId,
            UUID targetUserId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        Objects.requireNonNull(sourceUserId, "sourceUserId");
        Objects.requireNonNull(targetUserId, "targetUserId");
        Objects.requireNonNull(currencyId, "currencyId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(operationId, "operationId");

        Instant now = clock.instant();
        return storage.executor().transaction(tx -> {
            EconomyAccount source = getOrCreateLocked(tx, sourceUserId, currencyId);
            EconomyAccount target = getOrCreateLocked(tx, targetUserId, currencyId);
            EconomyAccount updatedSource = source.withdraw(amount, now);
            EconomyAccount updatedTarget = target.deposit(amount, now);
            ensureMaximumBalance(updatedTarget);
            EconomyTransaction transaction = EconomyTransaction.transfer(
                    operationId,
                    sourceUserId,
                    targetUserId,
                    currencyId,
                    amount,
                    source.balance(),
                    updatedSource.balance(),
                    target.balance(),
                    updatedTarget.balance(),
                    reason,
                    now);
            return transferAtomic(tx, sourceUserId, targetUserId, currencyId, amount, now)
                    .thenCompose(success -> success
                            ? insertTransaction(tx, transaction).thenApply(_ -> transaction)
                            : java.util.concurrent.CompletableFuture.<EconomyTransaction>failedFuture(
                                    new com.cotani.economy.exception.InsufficientFundsException(
                                            sourceUserId, source.balance(), amount)));
        });
    }

    private EconomyAccount getOrCreateLocked(QueryExecutor tx, UUID userId, CurrencyId currencyId) {
        return find(tx, userId, currencyId).orElseGet(() -> {
            Instant now = clock.instant();
            EconomyAccount created = EconomyAccount.create(userId, currencyId, settings.startingBalance(), now);
            var _ = insertAccount(tx, created).toCompletableFuture().join();
            return created;
        });
    }

    private Optional<EconomyAccount> find(QueryExecutor tx, UUID userId, CurrencyId currencyId) {
        String sql =
                "SELECT balance, created_at, updated_at FROM cotani_economy_accounts WHERE user_id = ? AND currency_id = ?";
        return tx.queryOne(
                        sql,
                        binder -> {
                            binder.set(userId);
                            binder.set(currencyId.value());
                        },
                        row -> accountFromRow(userId, currencyId, row))
                .toCompletableFuture()
                .join();
    }

    private static EconomyAccount accountFromRow(UUID userId, CurrencyId currencyId, Row row) throws SQLException {
        return EconomyStorageMappers.accountFromRow(userId, currencyId, row);
    }

    private CompletionStage<Void> insertAccount(QueryExecutor tx, EconomyAccount account) {
        String sql = storage.dialect()
                .upsert(
                        "cotani_economy_accounts",
                        List.of("user_id", "currency_id", "balance", "created_at", "updated_at"),
                        List.of("user_id", "currency_id"),
                        List.of("balance", "updated_at"));
        return tx.update(sql, binder -> {
            binder.set(account.userId());
            binder.set(account.currencyId().value());
            binder.set(account.balance().toPlainString());
            binder.set(account.createdAt().toString());
            binder.set(account.updatedAt().toString());
        });
    }

    private CompletionStage<Void> upsertAccount(QueryExecutor tx, EconomyAccount account) {
        return insertAccount(tx, account);
    }

    private CompletionStage<Boolean> updateBalanceIfSufficient(
            QueryExecutor tx,
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            BigDecimal newBalance,
            Instant now) {
        String sql =
                "UPDATE cotani_economy_accounts SET balance = ?, updated_at = ? WHERE user_id = ? AND currency_id = ? AND balance >= ?";
        return tx.update(sql, binder -> {
                    binder.set(newBalance.toPlainString());
                    binder.set(now.toString());
                    binder.set(userId);
                    binder.set(currencyId.value());
                    binder.set(amount.toPlainString());
                })
                .thenApply(_ -> true)
                .exceptionallyCompose(error -> {
                    if (error instanceof com.cotani.storage.error.StorageException) {
                        return java.util.concurrent.CompletableFuture.completedFuture(false);
                    }
                    return java.util.concurrent.CompletableFuture.failedFuture(error);
                });
    }

    private CompletionStage<Boolean> transferAtomic(
            QueryExecutor tx,
            UUID sourceUserId,
            UUID targetUserId,
            CurrencyId currencyId,
            BigDecimal amount,
            Instant now) {
        EconomyAccount source = getOrCreateLocked(tx, sourceUserId, currencyId);
        BigDecimal newSourceBalance = source.balance().subtract(amount);
        return updateBalanceIfSufficient(tx, sourceUserId, currencyId, amount, newSourceBalance, now)
                .thenCompose(sourceSuccess -> {
                    if (!sourceSuccess) {
                        return java.util.concurrent.CompletableFuture.completedFuture(false);
                    }
                    return addToBalance(tx, targetUserId, currencyId, amount, now);
                });
    }

    private CompletionStage<Boolean> addToBalance(
            QueryExecutor tx, UUID userId, CurrencyId currencyId, BigDecimal amount, Instant now) {
        String sql =
                "UPDATE cotani_economy_accounts SET balance = balance + ?, updated_at = ? WHERE user_id = ? AND currency_id = ? AND balance + ? <= ?";
        return tx.update(sql, binder -> {
                    binder.set(amount.toPlainString());
                    binder.set(now.toString());
                    binder.set(userId);
                    binder.set(currencyId.value());
                    binder.set(amount.toPlainString());
                    binder.set(settings.maximumBalance().toPlainString());
                })
                .thenApply(_ -> true)
                .exceptionallyCompose(error -> {
                    if (error instanceof com.cotani.storage.error.StorageException) {
                        return java.util.concurrent.CompletableFuture.completedFuture(false);
                    }
                    return java.util.concurrent.CompletableFuture.failedFuture(error);
                });
    }

    private CompletionStage<Void> insertTransaction(QueryExecutor tx, EconomyTransaction transaction) {
        return EconomyStorageMappers.insertTransaction(tx, storage, transaction);
    }

    private void ensureMaximumBalance(EconomyAccount account) {
        if (account.balance().compareTo(settings.maximumBalance()) > 0) {
            throw new MaximumBalanceExceededException(account.userId(), account.balance(), settings.maximumBalance());
        }
    }
}
