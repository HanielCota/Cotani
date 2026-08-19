package com.cotani.economy.internal.storage;

import com.cotani.economy.account.EconomyAccount;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.exception.DuplicateEconomyOperationException;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import com.cotani.economy.transaction.EconomyTransactionId;
import com.cotani.economy.transaction.EconomyTransactionType;
import com.cotani.storage.error.StorageException;
import com.cotani.storage.query.ParameterBinder;
import com.cotani.storage.query.Row;
import com.cotani.storage.transaction.TransactionContext;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

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
                row.getUuidOptional("reason_actor_user_id").orElse(null));
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

    static CompletionStage<Void> insertTransaction(TransactionContext tx, EconomyTransaction transaction) {
        // Plain INSERT — never INSERT IGNORE / DO NOTHING. A unique violation on operation_id must
        // fail the surrounding transaction so balance mutations are rolled back (true idempotency).
        String sql = """
                INSERT INTO cotani_economy_transactions (
                    transaction_id, operation_id, type, source_user_id, target_user_id, currency_id, amount,
                    source_balance_before, source_balance_after, target_balance_before, target_balance_after,
                    reason_key, reason_source, reason_actor_user_id, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        return tx.update(sql, binder -> bindTransaction(binder, transaction)).exceptionallyCompose(error -> {
            if (isUniqueViolation(error)) {
                return CompletableFuture.failedFuture(
                        new DuplicateEconomyOperationException(transaction.operationId()));
            }

            return CompletableFuture.failedFuture(error);
        });
    }

    static void bindTransaction(ParameterBinder binder, EconomyTransaction transaction) throws SQLException {
        binder.set(transaction.id().value());
        binder.set(transaction.operationId().value());
        binder.set(transaction.type().name());
        binder.set(transaction.sourceUserId());
        binder.set(transaction.targetUserId());
        binder.set(transaction.currencyId().value());
        binder.set(transaction.amount().toPlainString());
        binder.set(plainString(transaction.sourceBalanceBefore()));
        binder.set(plainString(transaction.sourceBalanceAfter()));
        binder.set(plainString(transaction.targetBalanceBefore()));
        binder.set(plainString(transaction.targetBalanceAfter()));
        binder.set(transaction.reason().key());
        binder.set(transaction.reason().source());
        binder.set(transaction.reason().actorUserId());
        binder.set(transaction.createdAt());
    }

    static boolean isUniqueViolation(Throwable error) {
        Throwable cause = unwrap(error);

        while (cause != null) {
            if (cause instanceof SQLException sqlException && isUniqueSqlException(sqlException)) {
                return true;
            }
            if (cause instanceof StorageException storageException) {
                Throwable nested = storageException.error().cause();

                if (nested instanceof SQLException sqlException && isUniqueSqlException(sqlException)) {
                    return true;
                }

                cause = nested != null ? nested : cause.getCause();
                continue;
            }

            cause = cause.getCause();
        }
        return false;
    }

    private static boolean isUniqueSqlException(SQLException sqlException) {
        String sqlState = sqlException.getSQLState();

        if (sqlState != null && sqlState.startsWith("23")) {
            return true;
        }

        int errorCode = sqlException.getErrorCode();
        // MySQL/MariaDB ER_DUP_ENTRY = 1062; SQLite SQLITE_CONSTRAINT = 19
        if (errorCode == 1062 || errorCode == 19) {
            return true;
        }

        String message = sqlException.getMessage();

        if (message == null) {
            return false;
        }

        String lower = message.toLowerCase(Locale.ROOT);

        return lower.contains("unique") || lower.contains("duplicate");
    }

    private static Throwable unwrap(Throwable error) {
        Throwable cause = error;

        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static @Nullable String plainString(@Nullable BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static String requireString(Row row, String column) throws SQLException {
        return row.getString(column);
    }

    private static UUID requireUuid(Row row, String column) throws SQLException {
        return row.getUuidOptional(column)
                .orElseThrow(() -> new IllegalStateException("Column is SQL NULL: " + column));
    }

    private static Instant requireInstant(Row row, String column) throws SQLException {
        return row.getInstantOptional(column)
                .orElseThrow(() -> new IllegalStateException("Column is SQL NULL: " + column));
    }

    private static BigDecimal requireBigDecimal(Row row, String column) throws SQLException {
        return new BigDecimal(row.getString(column));
    }
}
