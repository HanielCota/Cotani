package com.cotani.economy.internal.storage;

import com.cotani.api.InternalApi;
import com.cotani.economy.EconomySettings;
import com.cotani.economy.account.EconomyAccount;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.exception.DuplicateEconomyOperationException;
import com.cotani.economy.exception.InsufficientFundsException;
import com.cotani.economy.exception.MaximumBalanceExceededException;
import com.cotani.economy.internal.EconomyOperationFingerprint;
import com.cotani.economy.internal.repository.EconomyAccountRepository;
import com.cotani.economy.internal.repository.EconomyTransferRepository;
import com.cotani.economy.transaction.EconomyBalanceChange;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import com.cotani.economy.transaction.EconomyTransactionDetails;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.query.ParameterBinder;
import com.cotani.storage.query.Row;
import com.cotani.storage.transaction.TransactionContext;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * SQL-backed implementation of economy persistence.
 *
 * <p>Operations run on the storage executor provided by {@link CotaniStorage}. Repeating the same
 * {@link EconomyOperationId} returns the previously committed transaction without mutating balances
 * again.
 */
@InternalApi
public final class SqlEconomyStore implements EconomyAccountRepository, EconomyTransferRepository {
    private static final String USER_ID_PARAM = "userId";
    private static final String CURRENCY_ID_PARAM = "currencyId";
    private static final String AMOUNT_PARAM = "amount";
    private static final String REASON_PARAM = "reason";
    private static final String OPERATION_ID_PARAM = "operationId";
    private static final String USER_ID_COLUMN = "user_id";
    private static final String CURRENCY_ID_COLUMN = "currency_id";
    private static final String BALANCE_COLUMN = "balance";
    private static final String CREATED_AT_COLUMN = "created_at";
    private static final String UPDATED_AT_COLUMN = "updated_at";
    private static final List<String> ACCOUNT_COLUMNS =
            List.of(USER_ID_COLUMN, CURRENCY_ID_COLUMN, BALANCE_COLUMN, CREATED_AT_COLUMN, UPDATED_AT_COLUMN);
    private static final List<String> ACCOUNT_KEY_COLUMNS = List.of(USER_ID_COLUMN, CURRENCY_ID_COLUMN);
    private static final List<String> ACCOUNT_UPDATE_COLUMNS = List.of(BALANCE_COLUMN, UPDATED_AT_COLUMN);

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
        Objects.requireNonNull(userId, USER_ID_PARAM);
        Objects.requireNonNull(currencyId, CURRENCY_ID_PARAM);

        return storage.transactions().run(tx -> getOrCreateLocked(tx, userId, currencyId));
    }

    @Override
    public CompletionStage<EconomyTransaction> deposit(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId) {
        Objects.requireNonNull(userId, USER_ID_PARAM);
        Objects.requireNonNull(currencyId, CURRENCY_ID_PARAM);
        Objects.requireNonNull(amount, AMOUNT_PARAM);
        Objects.requireNonNull(reason, REASON_PARAM);
        Objects.requireNonNull(operationId, OPERATION_ID_PARAM);

        Instant now = clock.instant();

        return runIdempotent(
                operationId,
                EconomyOperationFingerprint.deposit(userId, currencyId, amount, reason),
                tx -> getOrCreateLocked(tx, userId, currencyId).thenCompose(account -> {
                    EconomyAccount updated = account.deposit(amount, now);
                    ensureMaximumBalance(updated);
                    EconomyTransaction transaction = EconomyTransaction.deposit(
                            new EconomyTransactionDetails(operationId, currencyId, amount, reason, now),
                            new EconomyBalanceChange(userId, account.balance(), updated.balance()));

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
        Objects.requireNonNull(userId, USER_ID_PARAM);
        Objects.requireNonNull(currencyId, CURRENCY_ID_PARAM);
        Objects.requireNonNull(amount, AMOUNT_PARAM);
        Objects.requireNonNull(reason, REASON_PARAM);
        Objects.requireNonNull(operationId, OPERATION_ID_PARAM);

        Instant now = clock.instant();

        return runIdempotent(
                operationId,
                EconomyOperationFingerprint.withdraw(userId, currencyId, amount, reason),
                tx -> getOrCreateLocked(tx, userId, currencyId).thenCompose(account -> {
                    EconomyAccount updated = account.withdraw(amount, now);
                    EconomyTransaction transaction = EconomyTransaction.withdraw(
                            new EconomyTransactionDetails(operationId, currencyId, amount, reason, now),
                            new EconomyBalanceChange(userId, account.balance(), updated.balance()));

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
        Objects.requireNonNull(userId, USER_ID_PARAM);
        Objects.requireNonNull(currencyId, CURRENCY_ID_PARAM);
        Objects.requireNonNull(amount, AMOUNT_PARAM);
        Objects.requireNonNull(reason, REASON_PARAM);
        Objects.requireNonNull(operationId, OPERATION_ID_PARAM);

        Instant now = clock.instant();

        return runIdempotent(
                operationId,
                EconomyOperationFingerprint.set(userId, currencyId, amount, reason),
                tx -> getOrCreateLocked(tx, userId, currencyId).thenCompose(account -> {
                    EconomyAccount updated = account.setBalance(amount, now);
                    ensureMaximumBalance(updated);
                    EconomyTransaction transaction = EconomyTransaction.set(
                            new EconomyTransactionDetails(operationId, currencyId, amount, reason, now),
                            new EconomyBalanceChange(userId, account.balance(), updated.balance()));

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
        Objects.requireNonNull(currencyId, CURRENCY_ID_PARAM);
        Objects.requireNonNull(amount, AMOUNT_PARAM);
        Objects.requireNonNull(reason, REASON_PARAM);
        Objects.requireNonNull(operationId, OPERATION_ID_PARAM);

        Instant now = clock.instant();

        if (sourceUserId.equals(targetUserId)) {
            return runIdempotent(
                    operationId,
                    EconomyOperationFingerprint.transfer(sourceUserId, targetUserId, currencyId, amount, reason),
                    tx -> getOrCreateLocked(tx, sourceUserId, currencyId).thenCompose(account -> {
                        if (account.balance().compareTo(amount) < 0) {
                            throw new InsufficientFundsException(sourceUserId, account.balance(), amount);
                        }
                        EconomyTransaction transaction = EconomyTransaction.transfer(
                                new EconomyTransactionDetails(operationId, currencyId, amount, reason, now),
                                new EconomyBalanceChange(sourceUserId, account.balance(), account.balance()),
                                new EconomyBalanceChange(targetUserId, account.balance(), account.balance()));
                        return insertTransaction(tx, transaction).thenApply(_ -> transaction);
                    }));
        }

        UUID firstId = sourceUserId.compareTo(targetUserId) <= 0 ? sourceUserId : targetUserId;
        UUID secondId = firstId.equals(sourceUserId) ? targetUserId : sourceUserId;

        return runIdempotent(
                operationId,
                EconomyOperationFingerprint.transfer(sourceUserId, targetUserId, currencyId, amount, reason),
                tx -> getOrCreateLocked(tx, firstId, currencyId)
                        .thenCompose(firstAccount -> getOrCreateLocked(tx, secondId, currencyId)
                                .thenCompose(secondAccount -> {
                                    EconomyAccount source = firstId.equals(sourceUserId) ? firstAccount : secondAccount;
                                    EconomyAccount target = firstId.equals(targetUserId) ? firstAccount : secondAccount;

                                    EconomyAccount updatedSource = source.withdraw(amount, now);
                                    EconomyAccount updatedTarget = target.deposit(amount, now);
                                    ensureMaximumBalance(updatedTarget);

                                    EconomyTransaction transaction = EconomyTransaction.transfer(
                                            new EconomyTransactionDetails(operationId, currencyId, amount, reason, now),
                                            new EconomyBalanceChange(
                                                    sourceUserId, source.balance(), updatedSource.balance()),
                                            new EconomyBalanceChange(
                                                    targetUserId, target.balance(), updatedTarget.balance()));

                                    return upsertAccount(tx, updatedSource)
                                            .thenCompose(_ -> upsertAccount(tx, updatedTarget))
                                            .thenCompose(_ -> insertTransaction(tx, transaction))
                                            .thenApply(_ -> transaction);
                                })));
    }

    private CompletionStage<EconomyTransaction> runIdempotent(
            EconomyOperationId operationId,
            EconomyOperationFingerprint fingerprint,
            Function<TransactionContext, CompletionStage<EconomyTransaction>> mutation) {
        return storage.transactions()
                .run(tx -> findTransactionByOperationId(tx, operationId).thenCompose(existing -> {
                    if (existing.isPresent()) {
                        return CompletableFuture.completedStage(fingerprint.requireMatch(operationId, existing.get()));
                    }

                    return mutation.apply(tx);
                }))
                .exceptionallyCompose(error -> {
                    if (!(unwrap(error) instanceof DuplicateEconomyOperationException)
                            && !EconomyStorageMappers.isUniqueViolation(error)) {
                        return CompletableFuture.failedFuture(error);
                    }

                    return findTransactionByOperationIdAsync(operationId)
                            .thenCompose(found -> found.map(existing -> CompletableFuture.completedStage(
                                            fingerprint.requireMatch(operationId, existing)))
                                    .orElseGet(() -> CompletableFuture.failedFuture(
                                            error instanceof DuplicateEconomyOperationException
                                                    ? error
                                                    : new DuplicateEconomyOperationException(operationId))));
                });
    }

    private CompletionStage<Optional<EconomyTransaction>> findTransactionByOperationIdAsync(
            EconomyOperationId operationId) {
        return storage.table("cotani_economy_transactions")
                .select()
                .where("operation_id", operationId.value())
                .one(EconomyStorageMappers::transactionFromRow);
    }

    private CompletionStage<Optional<EconomyTransaction>> findTransactionByOperationId(
            TransactionContext tx, EconomyOperationId operationId) {
        String sql = """
                SELECT transaction_id, operation_id, type, source_user_id, target_user_id, currency_id, amount,
                       source_balance_before, source_balance_after, target_balance_before, target_balance_after,
                       reason_key, reason_source, reason_actor_user_id, created_at
                FROM cotani_economy_transactions
                WHERE operation_id = ?
                """;

        return tx.queryOne(sql, binder -> binder.set(operationId.value()), EconomyStorageMappers::transactionFromRow);
    }

    private CompletionStage<EconomyAccount> getOrCreateLocked(
            TransactionContext tx, UUID userId, CurrencyId currencyId) {
        return findLocked(tx, userId, currencyId)
                .thenCompose(found -> found.map(CompletableFuture::completedStage)
                        .orElseGet(() -> {
                            Instant now = clock.instant();
                            EconomyAccount created = EconomyAccount.create(
                                    userId, currencyId, settings.startingBalance(currencyId), now);

                            return insertAccountDoNothing(tx, created)
                                    .thenCompose(_ -> findLocked(tx, userId, currencyId))
                                    .thenApply(reloaded -> reloaded.orElseThrow(() -> new IllegalStateException(
                                            "Could not load economy account after insert for " + userId)));
                        }));
    }

    private CompletionStage<Optional<EconomyAccount>> findLocked(
            TransactionContext tx, UUID userId, CurrencyId currencyId) {
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

    private CompletionStage<Void> insertAccountDoNothing(TransactionContext tx, EconomyAccount account) {
        String sql =
                storage.dialect().upsert("cotani_economy_accounts", ACCOUNT_COLUMNS, ACCOUNT_KEY_COLUMNS, List.of());

        return tx.update(sql, binder -> {
            bindAccount(binder, account);
        });
    }

    private CompletionStage<Void> insertAccount(TransactionContext tx, EconomyAccount account) {
        String sql = storage.dialect()
                .upsert("cotani_economy_accounts", ACCOUNT_COLUMNS, ACCOUNT_KEY_COLUMNS, ACCOUNT_UPDATE_COLUMNS);
        return tx.update(sql, binder -> {
            bindAccount(binder, account);
        });
    }

    static void bindAccount(ParameterBinder binder, EconomyAccount account) throws SQLException {
        binder.set(account.userId());
        binder.set(account.currencyId().value());
        binder.set(account.balance().toPlainString());
        binder.set(account.createdAt());
        binder.set(account.updatedAt());
    }

    private CompletionStage<Void> upsertAccount(TransactionContext tx, EconomyAccount account) {
        return insertAccount(tx, account);
    }

    private CompletionStage<Void> insertTransaction(TransactionContext tx, EconomyTransaction transaction) {
        return EconomyStorageMappers.insertTransaction(tx, transaction);
    }

    private void ensureMaximumBalance(EconomyAccount account) {
        var maximumBalance = settings.maximumBalance(account.currencyId());

        if (account.balance().compareTo(maximumBalance) > 0) {
            throw new MaximumBalanceExceededException(account.userId(), account.balance(), maximumBalance);
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable cause = error;

        while (cause.getCause() != null
                && (cause instanceof CompletionException || cause instanceof ExecutionException)) {
            cause = cause.getCause();
        }
        return cause;
    }
}
