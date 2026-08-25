package com.cotani.season.storage;

import com.cotani.season.api.SeasonExperienceCommand;
import com.cotani.season.api.SeasonExperienceConflictException;
import com.cotani.season.api.SeasonId;
import com.cotani.season.api.SeasonProgress;
import com.cotani.season.api.SeasonProgressConflictException;
import com.cotani.season.api.SeasonRepository;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import com.cotani.storage.query.Row;
import com.cotani.storage.transaction.TransactionContext;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/** SQL-backed optimistic season progress repository with idempotent experience operations. */
public final class StorageSeasonRepository implements SeasonRepository {
    private static final String PROGRESS_TABLE = "cotani_seasons_progress";
    private static final String OPERATIONS_TABLE = "cotani_seasons_experience_operations";
    private final CotaniStorage storage;

    public StorageSeasonRepository(CotaniStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public CompletionStage<Optional<SeasonProgress>> findAsync(UUID playerId, SeasonId seasonId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(seasonId, "seasonId");
        return storage.queryExecutor()
                .queryOne(
                        selectProgressSql(false),
                        binder -> {
                            binder.uuid(playerId);
                            binder.string(seasonId.value());
                        },
                        StorageSeasonRepository::mapProgress);
    }

    @Override
    public CompletionStage<SeasonProgress> applyExperienceAsync(SeasonExperienceCommand command) {
        Objects.requireNonNull(command, "command");
        return storage.transactions().runAsync(context -> applyInTransaction(context, command));
    }

    @Override
    public CompletionStage<SeasonProgress> saveAsync(SeasonProgress progress, long expectedRevision) {
        Objects.requireNonNull(progress, "progress");
        if (expectedRevision < 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("expectedRevision cannot be negative"));
        }
        return storage.transactions().runAsync(context -> saveInTransaction(context, progress, expectedRevision));
    }

    @Override
    public CompletionStage<Void> purgeExperienceOperationsBeforeAsync(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        return storage.transactions()
                .runAsync(transaction -> transaction.update(
                        "DELETE FROM " + OPERATIONS_TABLE + " WHERE occurred_at < ?", binder -> binder.set(cutoff)));
    }

    public static List<Migration> migrations() {
        return List.of(
                new CreateSeasonTablesMigration(),
                new CreateSeasonExperienceOperationsMigration(),
                new CreateSeasonIndexesMigration());
    }

    private CompletionStage<SeasonProgress> applyInTransaction(
            TransactionContext transaction, SeasonExperienceCommand command) {
        return transaction
                .queryOne(
                        operationSql(),
                        binder -> binder.uuid(command.operationId().value()),
                        StorageSeasonRepository::mapOperation)
                .thenCompose(existingOperation -> {
                    if (existingOperation.isPresent()) {
                        var row = existingOperation.orElseThrow();
                        var sameCommand = command.playerId().equals(row.playerId())
                                && command.seasonId().value().equals(row.seasonId())
                                && command.amount() == row.amount();
                        if (!sameCommand) {
                            return CompletableFuture.failedFuture(
                                    new SeasonExperienceConflictException(command.operationId()));
                        }
                        return transaction
                                .queryOne(
                                        selectProgressSql(false),
                                        binder -> {
                                            binder.uuid(command.playerId());
                                            binder.string(command.seasonId().value());
                                        },
                                        StorageSeasonRepository::mapProgress)
                                .thenCompose(progress -> progress.map(CompletableFuture::completedFuture)
                                        .orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException(
                                                "Experience operation has no progress row"))));
                    }

                    return transaction
                            .queryOne(
                                    selectProgressSql(true),
                                    binder -> {
                                        binder.uuid(command.playerId());
                                        binder.string(command.seasonId().value());
                                    },
                                    StorageSeasonRepository::mapProgress)
                            .thenCompose(existingProgress -> {
                                var current = existingProgress.orElseGet(
                                        () -> SeasonProgress.initial(command.playerId(), command.seasonId()));
                                final long experience;
                                try {
                                    experience = Math.addExact(current.experience(), command.amount());
                                } catch (ArithmeticException overflow) {
                                    return CompletableFuture.failedFuture(overflow);
                                }
                                var next = new SeasonProgress(
                                        current.playerId(),
                                        current.seasonId(),
                                        experience,
                                        current.claimedLevels(),
                                        current.revision());
                                var saved = next.withRevision(current.revision() + 1);
                                var persist = existingProgress.isPresent()
                                        ? updateProgress(transaction, saved, current.revision())
                                        : insertProgress(transaction, saved);
                                return persist.thenCompose(ignored -> insertOperation(transaction, command))
                                        .thenApply(ignored -> saved);
                            });
                });
    }

    private CompletionStage<SeasonProgress> saveInTransaction(
            TransactionContext transaction, SeasonProgress progress, long expectedRevision) {
        return transaction
                .queryOne(
                        selectProgressSql(true),
                        binder -> {
                            binder.uuid(progress.playerId());
                            binder.string(progress.seasonId().value());
                        },
                        StorageSeasonRepository::mapProgress)
                .thenCompose(existing -> {
                    if (existing.isPresent()) {
                        var current = existing.orElseThrow();
                        if (current.revision() != expectedRevision) {
                            return CompletableFuture.failedFuture(
                                    new SeasonProgressConflictException(current, expectedRevision));
                        }
                        return updateProgress(
                                        transaction, progress.withRevision(expectedRevision + 1), expectedRevision)
                                .thenApply(ignored -> progress.withRevision(expectedRevision + 1));
                    }
                    if (expectedRevision != 0) {
                        return CompletableFuture.failedFuture(new SeasonProgressConflictException(
                                SeasonProgress.initial(progress.playerId(), progress.seasonId()), expectedRevision));
                    }
                    return insertProgress(transaction, progress.withRevision(1))
                            .thenApply(ignored -> progress.withRevision(1))
                            .exceptionallyCompose(failure -> {
                                if (isUniqueConstraint(failure)) {
                                    return CompletableFuture.failedFuture(new SeasonProgressConflictException(
                                            SeasonProgress.initial(progress.playerId(), progress.seasonId()),
                                            expectedRevision));
                                }
                                return CompletableFuture.failedFuture(failure);
                            });
                });
    }

    private CompletionStage<Void> insertProgress(TransactionContext transaction, SeasonProgress progress) {
        return transaction.update(
                "INSERT INTO " + PROGRESS_TABLE
                        + " (player_id, season_id, experience, claimed_levels, revision) VALUES (?, ?, ?, ?, ?)",
                binder -> {
                    binder.uuid(progress.playerId());
                    binder.string(progress.seasonId().value());
                    binder.longValue(progress.experience());
                    binder.string(encodeClaimedLevels(progress.claimedLevels()));
                    binder.longValue(progress.revision());
                });
    }

    private CompletionStage<Void> updateProgress(
            TransactionContext transaction, SeasonProgress progress, long expectedRevision) {
        return transaction.update(
                "UPDATE " + PROGRESS_TABLE
                        + " SET experience = ?, claimed_levels = ?, revision = ?"
                        + " WHERE player_id = ? AND season_id = ? AND revision = ?",
                binder -> {
                    binder.longValue(progress.experience());
                    binder.string(encodeClaimedLevels(progress.claimedLevels()));
                    binder.longValue(progress.revision());
                    binder.uuid(progress.playerId());
                    binder.string(progress.seasonId().value());
                    binder.longValue(expectedRevision);
                });
    }

    private CompletionStage<Void> insertOperation(TransactionContext transaction, SeasonExperienceCommand command) {
        return transaction.update(
                "INSERT INTO " + OPERATIONS_TABLE
                        + " (operation_id, player_id, season_id, amount, occurred_at) VALUES (?, ?, ?, ?, ?)",
                binder -> {
                    binder.uuid(command.operationId().value());
                    binder.uuid(command.playerId());
                    binder.string(command.seasonId().value());
                    binder.longValue(command.amount());
                    binder.set(command.occurredAt());
                });
    }

    private static SeasonProgress mapProgress(Row row) throws SQLException {
        return new SeasonProgress(
                row.getUuidOptional("player_id").orElseThrow(),
                SeasonId.of(row.getString("season_id")),
                row.getLong("experience"),
                decodeClaimedLevels(row.getString("claimed_levels")),
                row.getLong("revision"));
    }

    private static OperationRecord mapOperation(Row row) throws SQLException {
        return new OperationRecord(
                row.getUuidOptional("player_id").orElseThrow(), row.getString("season_id"), row.getLong("amount"));
    }

    private String selectProgressSql(boolean forUpdate) {
        return "SELECT player_id, season_id, experience, claimed_levels, revision FROM " + PROGRESS_TABLE
                + " WHERE player_id = ? AND season_id = ?"
                + (forUpdate && !storage.dialect().name().equals("sqlite") ? " FOR UPDATE" : "");
    }

    private static String operationSql() {
        return "SELECT operation_id, player_id, season_id, amount, occurred_at FROM " + OPERATIONS_TABLE
                + " WHERE operation_id = ?";
    }

    private static String encodeClaimedLevels(Set<Integer> levels) {
        return levels.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static Set<Integer> decodeClaimedLevels(String encoded) {
        if (encoded.isBlank()) {
            return Set.of();
        }
        var decoded = new HashSet<Integer>();
        for (var value : encoded.split(",", -1)) {
            var level = Integer.parseInt(value);
            if (level <= 0 || !decoded.add(level)) {
                throw new IllegalArgumentException("Invalid claimed season level encoding");
            }
        }
        return Set.copyOf(decoded);
    }

    private static boolean isUniqueConstraint(Throwable failure) {
        var current = failure;
        while (current != null) {
            if (current instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
            if (current instanceof SQLException sqlException) {
                var state = sqlException.getSQLState();
                if ((state != null && state.startsWith("23")) || sqlException.getErrorCode() == 19) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private record OperationRecord(UUID playerId, String seasonId, long amount) {}
}
