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
import java.util.concurrent.CompletableFuture;
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

    private static EconomyAccount accountFromRow(UUID userId, CurrencyId currencyId, Row row) throws SQLException {
        return EconomyStorageMappers.accountFromRow(userId, currencyId, row);
    }

    @Override
    public CompletionStage<EconomyAccount> getOrCreate(UUID userId, CurrencyId currencyId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(currencyId, "currencyId");

        return storage.executor().transaction(tx -> getOrCreateLocked(tx, userId, currencyId));
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
        return storage.executor()
                .transaction(tx -> getOrCreateLocked(tx, userId, currencyId).thenCompose(account -> {
                    EconomyAccount updated = account.deposit(amount, now);
                    ensureMaximumBalance(updated);
                    EconomyTransaction transaction = EconomyTransaction.deposit(
                            operationId, userId, currencyId, amount, account.balance(), updated.balance(), reason, now);
                    return insertAccount(tx, updated)
                            .thenCompose(_ -> insertTransaction(tx, transaction))
                            .thenApply(_ -> transaction);
                }));
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
        return storage.executor()
                .transaction(tx -> getOrCreateLocked(tx, userId, currencyId).thenCompose(account -> {
                    EconomyAccount updated = account.withdraw(amount, now);
                    EconomyTransaction transaction = EconomyTransaction.withdraw(
                            operationId, userId, currencyId, amount, account.balance(), updated.balance(), reason, now);
                    return upsertAccount(tx, updated)
                            .thenCompose(_ -> insertTransaction(tx, transaction))
                            .thenApply(_ -> transaction);
                }));
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
        return storage.executor()
                .transaction(tx -> getOrCreateLocked(tx, userId, currencyId).thenCompose(account -> {
                    EconomyAccount updated = account.setBalance(amount, now);
                    ensureMaximumBalance(updated);
                    EconomyTransaction transaction = EconomyTransaction.set(
                            operationId, userId, currencyId, amount, account.balance(), updated.balance(), reason, now);
                    return upsertAccount(tx, updated)
                            .thenCompose(_ -> insertTransaction(tx, transaction))
                            .thenApply(_ -> transaction);
                }));
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

        // Lock in consistent order to prevent deadlocks
        UUID firstId = sourceUserId.compareTo(targetUserId) <= 0 ? sourceUserId : targetUserId;
        UUID secondId = firstId.equals(sourceUserId) ? targetUserId : sourceUserId;

        Instant now = clock.instant();
        return storage.executor()
                .transaction(tx -> getOrCreateLocked(tx, firstId, currencyId)
                        .thenCompose(firstAccount -> getOrCreateLocked(tx, secondId, currencyId)
                                .thenCompose(secondAccount -> {
                                    EconomyAccount source = firstId.equals(sourceUserId) ? firstAccount : secondAccount;
                                    EconomyAccount target = firstId.equals(targetUserId) ? firstAccount : secondAccount;

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

                                    return upsertAccount(tx, updatedSource)
                                            .thenCompose(_ -> upsertAccount(tx, updatedTarget))
                                            .thenCompose(_ -> insertTransaction(tx, transaction))
                                            .thenApply(_ -> transaction);
                                })));
    }

    private CompletionStage<EconomyAccount> getOrCreateLocked(QueryExecutor tx, UUID userId, CurrencyId currencyId) {
        return findLocked(tx, userId, currencyId)
                .thenCompose(found -> found.map(CompletableFuture::completedStage)
                        .orElseGet(() -> {
                            Instant now = clock.instant();
                            EconomyAccount created =
                                    EconomyAccount.create(userId, currencyId, settings.startingBalance(), now);
                            return insertAccount(tx, created).thenApply(_ -> created);
                        }));
    }

    private CompletionStage<Optional<EconomyAccount>> findLocked(QueryExecutor tx, UUID userId, CurrencyId currencyId) {
        String sql =
                "SELECT balance, created_at, updated_at FROM cotani_economy_accounts WHERE user_id = ? AND currency_id"
                        + " = ?";
        if (!storage.dialect().name().equalsIgnoreCase("sqlite")) {
            sql += " FOR UPDATE";
        }
        return tx.queryOne(
                sql,
                binder -> {
                    binder.set(userId);
                    binder.set(currencyId.value());
                },
                row -> accountFromRow(userId, currencyId, row));
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

    private CompletionStage<Void> insertTransaction(QueryExecutor tx, EconomyTransaction transaction) {
        return EconomyStorageMappers.insertTransaction(tx, storage, transaction);
    }

    private void ensureMaximumBalance(EconomyAccount account) {
        if (account.balance().compareTo(settings.maximumBalance()) > 0) {
            throw new MaximumBalanceExceededException(account.userId(), account.balance(), settings.maximumBalance());
        }
    }
}
