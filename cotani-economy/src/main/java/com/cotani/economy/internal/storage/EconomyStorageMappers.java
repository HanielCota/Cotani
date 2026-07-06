package com.cotani.economy.internal.storage;

import com.cotani.economy.account.EconomyAccount;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.exception.DuplicateEconomyOperationException;
import com.cotani.economy.transaction.*;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.executor.QueryExecutor;
import com.cotani.storage.query.Row;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

final class EconomyStorageMappers {

    private EconomyStorageMappers() {}

    static EconomyAccount accountFromRow(UUID userId, CurrencyId currencyId, Row row) throws SQLException {
        return new EconomyAccount(
                userId,
                currencyId,
                new BigDecimal(requireString(row, "balance")),
                requireInstant(row, "created_at"),
                requireInstant(row, "updated_at"));
    }

    static EconomyTransaction transactionFromRow(Row row) throws SQLException {
        EconomyTransactionId id = new EconomyTransactionId(requireUuid(row, "transaction_id"));
        EconomyOperationId operationId = new EconomyOperationId(requireUuid(row, "operation_id"));
        EconomyTransactionType type = EconomyTransactionType.valueOf(requireString(row, "type"));
        CurrencyId currencyId = CurrencyId.of(requireString(row, "currency_id"));
        BigDecimal amount = new BigDecimal(requireString(row, "amount"));
        EconomyReason reason = new EconomyReason(
                requireString(row, "reason_key"),
                requireString(row, "reason_source"),
                row.getUuid("reason_actor_user_id"));
        Instant createdAt = requireInstant(row, "created_at");

        return switch (type) {
            case DEPOSIT ->
                new EconomyTransaction.Deposit(
                        id,
                        operationId,
                        requireUuid(row, "target_user_id"),
                        currencyId,
                        amount,
                        Objects.requireNonNull(stringOrNull(row, "target_balance_before")),
                        Objects.requireNonNull(stringOrNull(row, "target_balance_after")),
                        reason,
                        createdAt);
            case WITHDRAW ->
                new EconomyTransaction.Withdraw(
                        id,
                        operationId,
                        requireUuid(row, "source_user_id"),
                        currencyId,
                        amount,
                        Objects.requireNonNull(stringOrNull(row, "source_balance_before")),
                        Objects.requireNonNull(stringOrNull(row, "source_balance_after")),
                        reason,
                        createdAt);
            case SET ->
                new EconomyTransaction.Set(
                        id,
                        operationId,
                        requireUuid(row, "target_user_id"),
                        currencyId,
                        amount,
                        Objects.requireNonNull(stringOrNull(row, "target_balance_before")),
                        Objects.requireNonNull(stringOrNull(row, "target_balance_after")),
                        reason,
                        createdAt);
            case TRANSFER ->
                new EconomyTransaction.Transfer(
                        id,
                        operationId,
                        requireUuid(row, "source_user_id"),
                        requireUuid(row, "target_user_id"),
                        currencyId,
                        amount,
                        Objects.requireNonNull(stringOrNull(row, "source_balance_before")),
                        Objects.requireNonNull(stringOrNull(row, "source_balance_after")),
                        Objects.requireNonNull(stringOrNull(row, "target_balance_before")),
                        Objects.requireNonNull(stringOrNull(row, "target_balance_after")),
                        reason,
                        createdAt);
        };
    }

    static CompletionStage<Void> insertTransaction(
            QueryExecutor tx, CotaniStorage storage, EconomyTransaction transaction) {
        String sql = storage.dialect()
                .upsert(
                        "cotani_economy_transactions",
                        List.of(
                                "transaction_id",
                                "operation_id",
                                "type",
                                "source_user_id",
                                "target_user_id",
                                "currency_id",
                                "amount",
                                "source_balance_before",
                                "source_balance_after",
                                "target_balance_before",
                                "target_balance_after",
                                "reason_key",
                                "reason_source",
                                "reason_actor_user_id",
                                "created_at"),
                        List.of("transaction_id"),
                        List.of());
        return tx.update(sql, binder -> {
                    binder.set(transaction.id().value());
                    binder.set(transaction.operationId().value());
                    binder.set(transaction.type().name());
                    binder.set(transaction.sourceUserId());
                    binder.set(transaction.targetUserId());
                    binder.set(transaction.currencyId().value());
                    binder.set(transaction.amount().toPlainString());
                    binder.set(transaction.sourceBalanceBefore());
                    binder.set(transaction.sourceBalanceAfter());
                    binder.set(transaction.targetBalanceBefore());
                    binder.set(transaction.targetBalanceAfter());
                    binder.set(transaction.reason().key());
                    binder.set(transaction.reason().source());
                    binder.set(transaction.reason().actorUserId());
                    binder.set(transaction.createdAt().toString());
                })
                .exceptionallyCompose(error -> {
                    Throwable cause = error;
                    while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    if (cause instanceof java.sql.SQLException sqlException
                            && sqlException.getMessage() != null
                            && sqlException
                                    .getMessage()
                                    .toLowerCase(Locale.ROOT)
                                    .contains("unique")) {
                        return java.util.concurrent.CompletableFuture.failedFuture(
                                new DuplicateEconomyOperationException(transaction.operationId()));
                    }
                    return java.util.concurrent.CompletableFuture.failedFuture(error);
                });
    }

    private static String requireString(Row row, String column) throws SQLException {
        return Objects.requireNonNull(row.getString(column), column);
    }

    private static UUID requireUuid(Row row, String column) throws SQLException {
        return Objects.requireNonNull(row.getUuid(column), column);
    }

    private static Instant requireInstant(Row row, String column) throws SQLException {
        return Objects.requireNonNull(row.getInstant(column), column);
    }

    private static BigDecimal stringOrNull(Row row, String column) throws SQLException {
        String raw = row.getString(column);
        return new BigDecimal(raw);
    }
}
