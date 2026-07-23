package com.cotani.economy.internal.storage;

import com.cotani.economy.account.EconomyAccount;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.exception.DuplicateEconomyOperationException;
import com.cotani.economy.transaction.*;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.query.Row;
import com.cotani.storage.transaction.TransactionContext;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

final class EconomyStorageMappers {

    private static final String CREATED_AT = "created_at";
    private static final String TRANSACTION_ID = "transaction_id";
    private static final String TARGET_USER_ID = "target_user_id";
    private static final String TARGET_BALANCE_BEFORE = "target_balance_before";
    private static final String TARGET_BALANCE_AFTER = "target_balance_after";
    private static final String SOURCE_USER_ID = "source_user_id";
    private static final String SOURCE_BALANCE_BEFORE = "source_balance_before";
    private static final String SOURCE_BALANCE_AFTER = "source_balance_after";

    private EconomyStorageMappers() {}

    static EconomyAccount accountFromRow(UUID userId, CurrencyId currencyId, Row row) throws SQLException {
        return new EconomyAccount(
                userId,
                currencyId,
                new BigDecimal(requireString(row, "balance")),
                requireInstant(row, CREATED_AT),
                requireInstant(row, "updated_at"));
    }

    static EconomyTransaction transactionFromRow(Row row) throws SQLException {
        EconomyTransactionId id = new EconomyTransactionId(requireUuid(row, TRANSACTION_ID));
        EconomyOperationId operationId = new EconomyOperationId(requireUuid(row, "operation_id"));
        EconomyTransactionType type = EconomyTransactionType.valueOf(requireString(row, "type"));
        CurrencyId currencyId = CurrencyId.of(requireString(row, "currency_id"));
        BigDecimal amount = new BigDecimal(requireString(row, "amount"));
        EconomyReason reason = new EconomyReason(
                requireString(row, "reason_key"),
                requireString(row, "reason_source"),
                row.getUuid("reason_actor_user_id"));
        Instant createdAt = requireInstant(row, CREATED_AT);

        return switch (type) {
            case DEPOSIT ->
                new EconomyTransaction.Deposit(
                        id,
                        operationId,
                        requireUuid(row, TARGET_USER_ID),
                        currencyId,
                        amount,
                        requireBigDecimal(row, TARGET_BALANCE_BEFORE),
                        requireBigDecimal(row, TARGET_BALANCE_AFTER),
                        reason,
                        createdAt);
            case WITHDRAW ->
                new EconomyTransaction.Withdraw(
                        id,
                        operationId,
                        requireUuid(row, SOURCE_USER_ID),
                        currencyId,
                        amount,
                        requireBigDecimal(row, SOURCE_BALANCE_BEFORE),
                        requireBigDecimal(row, SOURCE_BALANCE_AFTER),
                        reason,
                        createdAt);
            case SET ->
                new EconomyTransaction.Set(
                        id,
                        operationId,
                        requireUuid(row, TARGET_USER_ID),
                        currencyId,
                        amount,
                        requireBigDecimal(row, TARGET_BALANCE_BEFORE),
                        requireBigDecimal(row, TARGET_BALANCE_AFTER),
                        reason,
                        createdAt);
            case TRANSFER ->
                new EconomyTransaction.Transfer(
                        id,
                        operationId,
                        requireUuid(row, SOURCE_USER_ID),
                        requireUuid(row, TARGET_USER_ID),
                        currencyId,
                        amount,
                        requireBigDecimal(row, SOURCE_BALANCE_BEFORE),
                        requireBigDecimal(row, SOURCE_BALANCE_AFTER),
                        requireBigDecimal(row, TARGET_BALANCE_BEFORE),
                        requireBigDecimal(row, TARGET_BALANCE_AFTER),
                        reason,
                        createdAt);
        };
    }

    static CompletionStage<Void> insertTransaction(
            TransactionContext tx, CotaniStorage storage, EconomyTransaction transaction) {
        String sql = storage.dialect()
                .upsert(
                        "cotani_economy_transactions",
                        List.of(
                                TRANSACTION_ID,
                                "operation_id",
                                "type",
                                SOURCE_USER_ID,
                                TARGET_USER_ID,
                                "currency_id",
                                "amount",
                                SOURCE_BALANCE_BEFORE,
                                SOURCE_BALANCE_AFTER,
                                TARGET_BALANCE_BEFORE,
                                TARGET_BALANCE_AFTER,
                                "reason_key",
                                "reason_source",
                                "reason_actor_user_id",
                                CREATED_AT),
                        List.of(TRANSACTION_ID),
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
                    while (cause instanceof CompletionException && cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    if (cause instanceof SQLException sqlException
                            && sqlException.getMessage() != null
                            && sqlException
                                    .getMessage()
                                    .toLowerCase(Locale.ROOT)
                                    .contains("unique")) {
                        return CompletableFuture.failedFuture(
                                new DuplicateEconomyOperationException(transaction.operationId()));
                    }
                    return CompletableFuture.failedFuture(error);
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

    private static BigDecimal requireBigDecimal(Row row, String column) throws SQLException {
        String raw = Objects.requireNonNull(row.getString(column), column);
        return new BigDecimal(raw);
    }
}
