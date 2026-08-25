package com.cotani.statistics.storage;

import com.cotani.statistics.api.StatisticConflictException;
import com.cotani.statistics.api.StatisticEntry;
import com.cotani.statistics.api.StatisticId;
import com.cotani.statistics.api.StatisticOperationId;
import com.cotani.statistics.api.StatisticOverflowException;
import com.cotani.statistics.api.StatisticRankEntry;
import com.cotani.statistics.api.StatisticRepository;
import com.cotani.statistics.api.StatisticUpdate;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import com.cotani.storage.query.Row;
import com.cotani.storage.transaction.TransactionContext;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

/** SQL-backed repository for atomic, idempotent, non-negative player statistics. */
public final class StorageStatisticRepository implements StatisticRepository {
    private static final String TABLE = "cotani_statistics";
    private static final String OPERATIONS_TABLE = "cotani_statistics_operations";

    private final CotaniStorage storage;

    public StorageStatisticRepository(CotaniStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public CompletionStage<Optional<StatisticEntry>> findAsync(UUID playerId, StatisticId statisticId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(statisticId, "statisticId");
        return storage.queryExecutor()
                .queryOne(
                        selectSql(false),
                        binder -> {
                            binder.uuid(playerId);
                            binder.string(statisticId.value());
                        },
                        StorageStatisticRepository::mapEntry);
    }

    @Override
    public CompletionStage<StatisticUpdate> incrementAsync(
            UUID playerId, StatisticId statisticId, long amount, Instant updatedAt) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(statisticId, "statisticId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (amount <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("amount must be positive"));
        }

        return storage.transactions()
                .runAsync(context ->
                        incrementNewStatisticInTransaction(context, playerId, statisticId, amount, updatedAt))
                .handle((update, failure) -> new TransactionOutcome(update, unwrap(failure)))
                .thenCompose(outcome -> completeTransaction(outcome, playerId, statisticId));
    }

    @Override
    public CompletionStage<StatisticUpdate> incrementIdempotentlyAsync(
            UUID playerId, StatisticId statisticId, long amount, Instant updatedAt, StatisticOperationId operationId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(statisticId, "statisticId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(operationId, "operationId");
        if (amount <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("amount must be positive"));
        }
        return storage.transactions()
                .runAsync(context ->
                        incrementInTransaction(context, playerId, statisticId, amount, updatedAt, operationId))
                .handle((update, failure) -> new TransactionOutcome(update, unwrap(failure)))
                .thenCompose(outcome -> completeTransaction(outcome, playerId, statisticId));
    }

    private CompletionStage<StatisticUpdate> completeTransaction(
            TransactionOutcome outcome, UUID playerId, StatisticId statisticId) {
        if (outcome.failure() == null) {
            return CompletableFuture.completedFuture(
                    Objects.requireNonNull(outcome.update(), "transaction returned null update"));
        }
        if (isUniqueConstraint(outcome.failure())) {
            return CompletableFuture.failedFuture(new StatisticConflictException(playerId, statisticId));
        }
        return CompletableFuture.failedFuture(outcome.failure());
    }

    @Override
    public CompletionStage<List<StatisticRankEntry>> topAsync(StatisticId statisticId, int limit) {
        Objects.requireNonNull(statisticId, "statisticId");
        if (limit <= 0 || limit > 1_000) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("limit must be between 1 and 1000"));
        }
        return storage.queryExecutor()
                .queryMany(
                        "SELECT player_id, value FROM " + TABLE
                                + " WHERE statistic_id = ? ORDER BY value DESC, player_id ASC LIMIT ?",
                        binder -> {
                            binder.string(statisticId.value());
                            binder.integer(limit);
                        },
                        StorageStatisticRepository::mapRankValue)
                .thenApply(rows -> {
                    var result = new ArrayList<StatisticRankEntry>(rows.size());
                    for (int index = 0; index < rows.size(); index++) {
                        var row = rows.get(index);
                        result.add(new StatisticRankEntry(index + 1, row.playerId(), row.value()));
                    }
                    return List.copyOf(result);
                });
    }

    public static List<Migration> migrations() {
        return List.of(
                new CreateStatisticTablesMigration(),
                new CreateStatisticIndexesMigration(),
                new CreateStatisticOperationsMigration());
    }

    private CompletionStage<StatisticUpdate> incrementInTransaction(
            TransactionContext transaction,
            UUID playerId,
            StatisticId statisticId,
            long amount,
            Instant updatedAt,
            StatisticOperationId operationId) {
        return transaction
                .queryOne(
                        operationSelectSql(true),
                        binder -> binder.uuid(operationId.value()),
                        StorageStatisticRepository::mapOperation)
                .thenCompose(existingOperation -> {
                    if (existingOperation.isPresent()) {
                        return replayOperation(existingOperation.orElseThrow(), playerId, statisticId, amount);
                    }
                    return incrementNewOperation(transaction, playerId, statisticId, amount, updatedAt, operationId);
                });
    }

    private CompletionStage<StatisticUpdate> incrementNewOperation(
            TransactionContext transaction,
            UUID playerId,
            StatisticId statisticId,
            long amount,
            Instant updatedAt,
            StatisticOperationId operationId) {
        return incrementNewStatisticInTransaction(transaction, playerId, statisticId, amount, updatedAt)
                .thenCompose(update -> insertOperation(
                                transaction,
                                operationId,
                                playerId,
                                statisticId,
                                amount,
                                update.previousValue(),
                                update.current())
                        .thenApply(ignored -> update));
    }

    private CompletionStage<StatisticUpdate> incrementNewStatisticInTransaction(
            TransactionContext transaction, UUID playerId, StatisticId statisticId, long amount, Instant updatedAt) {
        return transaction
                .queryOne(
                        selectSql(true),
                        binder -> {
                            binder.uuid(playerId);
                            binder.string(statisticId.value());
                        },
                        StorageStatisticRepository::mapEntry)
                .thenCompose(existing -> {
                    var previous = existing.orElseGet(() -> StatisticEntry.initial(playerId, statisticId));
                    final long value;
                    final long revision;
                    try {
                        value = Math.addExact(previous.value(), amount);
                        revision = Math.addExact(previous.revision(), 1L);
                    } catch (ArithmeticException overflow) {
                        return CompletableFuture.failedFuture(new StatisticOverflowException(playerId, statisticId));
                    }
                    var current = new StatisticEntry(playerId, statisticId, value, updatedAt, revision);
                    CompletionStage<Void> persist = existing.isPresent()
                            ? update(transaction, current, previous.revision())
                            : insert(transaction, current);
                    return persist.thenApply(ignored -> new StatisticUpdate(amount, previous.value(), current));
                });
    }

    private CompletionStage<StatisticUpdate> replayOperation(
            OperationRecord operation, UUID playerId, StatisticId statisticId, long amount) {
        if (!operation.playerId().equals(playerId)
                || !operation.statisticId().equals(statisticId)
                || operation.amount() != amount) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("operation id was already used for another increment"));
        }
        return CompletableFuture.completedFuture(
                new StatisticUpdate(operation.amount(), operation.previousValue(), operation.current(), false));
    }

    private CompletionStage<Void> insert(TransactionContext transaction, StatisticEntry entry) {
        return transaction.update(
                "INSERT INTO " + TABLE
                        + " (player_id, statistic_id, value, updated_at, revision) VALUES (?, ?, ?, ?, ?)",
                binder -> {
                    binder.uuid(entry.playerId());
                    binder.string(entry.statisticId().value());
                    binder.longValue(entry.value());
                    binder.set(entry.updatedAt());
                    binder.longValue(entry.revision());
                });
    }

    private CompletionStage<Void> update(TransactionContext transaction, StatisticEntry entry, long expectedRevision) {
        return transaction.update(
                "UPDATE " + TABLE
                        + " SET value = ?, updated_at = ?, revision = ?"
                        + " WHERE player_id = ? AND statistic_id = ? AND revision = ?",
                binder -> {
                    binder.longValue(entry.value());
                    binder.set(entry.updatedAt());
                    binder.longValue(entry.revision());
                    binder.uuid(entry.playerId());
                    binder.string(entry.statisticId().value());
                    binder.longValue(expectedRevision);
                });
    }

    private CompletionStage<Void> insertOperation(
            TransactionContext transaction,
            StatisticOperationId operationId,
            UUID playerId,
            StatisticId statisticId,
            long amount,
            long previousValue,
            StatisticEntry current) {
        return transaction.update(
                "INSERT INTO " + OPERATIONS_TABLE
                        + " (operation_id, player_id, statistic_id, amount, previous_value, value, updated_at, revision) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                binder -> {
                    binder.uuid(operationId.value());
                    binder.uuid(playerId);
                    binder.string(statisticId.value());
                    binder.longValue(amount);
                    binder.longValue(previousValue);
                    binder.longValue(current.value());
                    binder.set(current.updatedAt());
                    binder.longValue(current.revision());
                });
    }

    private String selectSql(boolean forUpdate) {
        return "SELECT player_id, statistic_id, value, updated_at, revision FROM " + TABLE
                + " WHERE player_id = ? AND statistic_id = ?"
                + (forUpdate && !storage.dialect().name().equals("sqlite") ? " FOR UPDATE" : "");
    }

    private String operationSelectSql(boolean forUpdate) {
        return "SELECT operation_id, player_id, statistic_id, amount, previous_value, value, updated_at, revision "
                + "FROM " + OPERATIONS_TABLE + " WHERE operation_id = ?"
                + (forUpdate && !storage.dialect().name().equals("sqlite") ? " FOR UPDATE" : "");
    }

    private static StatisticEntry mapEntry(Row row) throws SQLException {
        return new StatisticEntry(
                row.getUuidOptional("player_id").orElseThrow(),
                StatisticId.of(row.getString("statistic_id")),
                row.getLong("value"),
                row.getInstantOptional("updated_at").orElseThrow(),
                row.getLong("revision"));
    }

    private static RankValue mapRankValue(Row row) throws SQLException {
        return new RankValue(row.getUuidOptional("player_id").orElseThrow(), row.getLong("value"));
    }

    private static OperationRecord mapOperation(Row row) throws SQLException {
        var playerId = row.getUuidOptional("player_id").orElseThrow();
        var statisticId = StatisticId.of(row.getString("statistic_id"));
        return new OperationRecord(
                playerId,
                statisticId,
                row.getLong("amount"),
                row.getLong("previous_value"),
                new StatisticEntry(
                        playerId,
                        statisticId,
                        row.getLong("value"),
                        row.getInstantOptional("updated_at").orElseThrow(),
                        row.getLong("revision")));
    }

    private static boolean isUniqueConstraint(Throwable failure) {
        var current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException && isDuplicateKey(sqlException)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isDuplicateKey(SQLException exception) {
        var message = Objects.toString(exception.getMessage(), "").toLowerCase(Locale.ROOT);
        var duplicateKeyMessage =
                message.contains("unique") || message.contains("primary key") || message.contains("duplicate");
        var state = exception.getSQLState();
        return duplicateKeyMessage
                && ((exception instanceof SQLIntegrityConstraintViolationException)
                        || (state != null && (state.equals("23000") || state.equals("23505")))
                        || exception.getErrorCode() == 19);
    }

    private static Throwable unwrap(Throwable failure) {
        var current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record RankValue(UUID playerId, long value) {}

    private record OperationRecord(
            UUID playerId, StatisticId statisticId, long amount, long previousValue, StatisticEntry current) {}

    private record TransactionOutcome(
            @org.jspecify.annotations.Nullable StatisticUpdate update,
            @org.jspecify.annotations.Nullable Throwable failure) {}
}
