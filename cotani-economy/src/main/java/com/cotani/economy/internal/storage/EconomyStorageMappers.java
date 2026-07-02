package com.cotani.economy.internal.storage;

import com.cotani.economy.transaction.EconomyTransaction;
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
import org.jspecify.annotations.Nullable;

final class EconomyStorageMappers {

    private EconomyStorageMappers() {}

    static com.cotani.economy.account.EconomyAccount accountFromRow(
            UUID userId, com.cotani.economy.currency.CurrencyId currencyId, Row row) throws SQLException {
        return new com.cotani.economy.account.EconomyAccount(
                userId,
                currencyId,
                new BigDecimal(requireString(row, "balance")),
                requireInstant(row, "created_at"),
                requireInstant(row, "updated_at"));
    }

    static EconomyTransaction transactionFromRow(Row row) throws SQLException {
        return new EconomyTransaction(
                new com.cotani.economy.transaction.EconomyTransactionId(requireUuid(row, "transaction_id")),
                new com.cotani.economy.transaction.EconomyOperationId(requireUuid(row, "operation_id")),
                com.cotani.economy.transaction.EconomyTransactionType.valueOf(requireString(row, "type")),
                row.getUuid("source_user_id"),
                row.getUuid("target_user_id"),
                com.cotani.economy.currency.CurrencyId.of(requireString(row, "currency_id")),
                new BigDecimal(requireString(row, "amount")),
                stringOrNull(row, "source_balance_before"),
                stringOrNull(row, "source_balance_after"),
                stringOrNull(row, "target_balance_before"),
                stringOrNull(row, "target_balance_after"),
                new com.cotani.economy.transaction.EconomyReason(
                        requireString(row, "reason_key"),
                        requireString(row, "reason_source"),
                        row.getUuid("reason_actor_user_id")),
                requireInstant(row, "created_at"));
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
                                new com.cotani.economy.exception.DuplicateEconomyOperationException(
                                        transaction.operationId()));
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

    private static @Nullable BigDecimal stringOrNull(Row row, String column) throws SQLException {
        String raw = row.getString(column);
        if (raw == null) {
            return null;
        }
        return new BigDecimal(raw);
    }
}
