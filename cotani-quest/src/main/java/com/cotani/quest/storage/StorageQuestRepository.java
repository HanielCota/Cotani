package com.cotani.quest.storage;

import com.cotani.quest.api.QuestClaimId;
import com.cotani.quest.api.QuestId;
import com.cotani.quest.api.QuestObjectiveId;
import com.cotani.quest.api.QuestProgress;
import com.cotani.quest.api.QuestProgressConflictException;
import com.cotani.quest.api.QuestRepository;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import com.cotani.storage.query.Row;
import com.cotani.storage.transaction.TransactionContext;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** SQL-backed optimistic quest progress repository. */
public final class StorageQuestRepository implements QuestRepository {
    private static final String TABLE = "cotani_quests_progress";
    private final CotaniStorage storage;

    public StorageQuestRepository(CotaniStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public CompletionStage<Optional<QuestProgress>> findAsync(UUID playerId, QuestId questId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(questId, "questId");
        return storage.queryExecutor()
                .queryOne(
                        selectSql(false),
                        binder -> {
                            binder.uuid(playerId);
                            binder.string(questId.value());
                        },
                        StorageQuestRepository::mapRow);
    }

    @Override
    public CompletionStage<QuestProgress> saveAsync(QuestProgress progress, long expectedRevision) {
        Objects.requireNonNull(progress, "progress");
        if (progress.revision() < 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("progress revision cannot be negative"));
        }
        if (expectedRevision < 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("expectedRevision cannot be negative"));
        }
        return storage.transactions().runAsync(context -> saveInTransaction(context, progress, expectedRevision));
    }

    public static List<Migration> migrations() {
        return List.of(new CreateQuestTablesMigration(), new CreateQuestIndexesMigration());
    }

    private CompletionStage<QuestProgress> saveInTransaction(
            TransactionContext transaction, QuestProgress progress, long expectedRevision) {
        return transaction
                .queryOne(
                        selectSql(true),
                        binder -> {
                            binder.uuid(progress.playerId());
                            binder.string(progress.questId().value());
                        },
                        StorageQuestRepository::mapRow)
                .thenCompose(existing -> {
                    if (existing.isPresent()) {
                        var current = existing.orElseThrow();
                        if (current.revision() != expectedRevision) {
                            return CompletableFuture.failedFuture(
                                    new QuestProgressConflictException(current, expectedRevision));
                        }
                        return update(transaction, progress, expectedRevision)
                                .thenApply(ignored -> progress.withRevision(expectedRevision + 1));
                    }
                    if (expectedRevision != 0) {
                        var initial = QuestProgress.initial(progress.playerId(), progress.questId());
                        return CompletableFuture.failedFuture(
                                new QuestProgressConflictException(initial, expectedRevision));
                    }
                    return insert(transaction, progress)
                            .thenApply(ignored -> progress.withRevision(1))
                            .exceptionallyCompose(failure -> {
                                if (isUniqueConstraint(failure)) {
                                    return CompletableFuture.failedFuture(new QuestProgressConflictException(
                                            QuestProgress.initial(progress.playerId(), progress.questId()),
                                            expectedRevision));
                                }
                                return CompletableFuture.failedFuture(failure);
                            });
                });
    }

    private CompletionStage<Void> insert(TransactionContext transaction, QuestProgress progress) {
        return transaction.update(
                "INSERT INTO " + TABLE
                        + " (player_id, quest_id, progress, completed, completed_at, claim_id, claimed_at, revision) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                binder -> {
                    binder.uuid(progress.playerId());
                    binder.string(progress.questId().value());
                    bindValues(binder, progress, 1);
                });
    }

    private CompletionStage<Void> update(
            TransactionContext transaction, QuestProgress progress, long expectedRevision) {
        return transaction.update(
                "UPDATE " + TABLE
                        + " SET progress = ?, completed = ?, completed_at = ?, claim_id = ?, claimed_at = ?, revision = ?"
                        + " WHERE player_id = ? AND quest_id = ? AND revision = ?",
                binder -> {
                    bindValues(binder, progress, expectedRevision + 1);
                    binder.uuid(progress.playerId());
                    binder.string(progress.questId().value());
                    binder.longValue(expectedRevision);
                });
    }

    private static void bindValues(
            com.cotani.storage.query.ParameterBinder binder, QuestProgress progress, long revision)
            throws SQLException {
        binder.string(encodeProgress(progress.objectiveProgress()));
        binder.set(progress.completed());
        binder.set(progress.completedAt().orElse(null));
        binder.set(
                progress.claimId().map(QuestClaimId::value).map(UUID::toString).orElse(null));
        binder.set(progress.claimedAt().orElse(null));
        binder.longValue(revision);
    }

    private static QuestProgress mapRow(Row row) throws SQLException {
        return new QuestProgress(
                row.getUuidOptional("player_id").orElseThrow(),
                QuestId.of(row.getString("quest_id")),
                decodeProgress(row.getString("progress")),
                row.getBoolean("completed"),
                row.getInstantOptional("completed_at"),
                row.getStringOptional("claim_id").map(UUID::fromString).map(QuestClaimId::new),
                row.getInstantOptional("claimed_at"),
                row.getLong("revision"));
    }

    private String selectSql(boolean forUpdate) {
        return "SELECT player_id, quest_id, progress, completed, completed_at, claim_id, claimed_at, revision FROM "
                + TABLE + " WHERE player_id = ? AND quest_id = ?"
                + (forUpdate && !storage.dialect().name().equals("sqlite") ? " FOR UPDATE" : "");
    }

    private static String encodeProgress(Map<QuestObjectiveId, Long> progress) {
        return progress.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(QuestObjectiveId::value)))
                .map(entry -> entry.getKey().value() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(";"));
    }

    private static Map<QuestObjectiveId, Long> decodeProgress(String encoded) {
        if (encoded.isBlank()) {
            return Map.of();
        }
        var decoded = new HashMap<QuestObjectiveId, Long>();
        for (var item : encoded.split(";", -1)) {
            var separator = item.indexOf('=');
            if (separator <= 0 || separator == item.length() - 1) {
                throw new IllegalArgumentException("Invalid quest progress encoding");
            }
            var objectiveId = QuestObjectiveId.of(item.substring(0, separator));
            var amount = Long.parseLong(item.substring(separator + 1));
            if (decoded.putIfAbsent(objectiveId, amount) != null) {
                throw new IllegalArgumentException("Duplicate objective in quest progress encoding");
            }
        }
        return Map.copyOf(decoded);
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
}
