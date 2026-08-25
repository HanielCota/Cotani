package com.cotani.achievement.storage;

import com.cotani.achievement.api.AchievementId;
import com.cotani.achievement.api.AchievementProgress;
import com.cotani.achievement.api.AchievementProgressConflictException;
import com.cotani.achievement.api.AchievementRepository;
import com.cotani.reward.api.RewardClaimId;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import com.cotani.storage.query.Row;
import com.cotani.storage.transaction.TransactionContext;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** SQL-backed optimistic repository for per-player achievement progress. */
public final class StorageAchievementRepository implements AchievementRepository {
    private static final String TABLE = "cotani_achievements_progress";

    private final CotaniStorage storage;

    public StorageAchievementRepository(CotaniStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public CompletionStage<Optional<AchievementProgress>> findAsync(UUID playerId, AchievementId achievementId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(achievementId, "achievementId");
        return storage.queryExecutor()
                .queryOne(
                        selectSql(false),
                        binder -> {
                            binder.uuid(playerId);
                            binder.string(achievementId.value());
                        },
                        StorageAchievementRepository::mapRow);
    }

    @Override
    public CompletionStage<AchievementProgress> saveAsync(AchievementProgress progress, long expectedRevision) {
        Objects.requireNonNull(progress, "progress");
        if (expectedRevision < 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("expectedRevision cannot be negative"));
        }
        return storage.transactions().runAsync(context -> saveInTransaction(context, progress, expectedRevision));
    }

    public static List<Migration> migrations() {
        return List.of(new CreateAchievementTablesMigration(), new CreateAchievementIndexesMigration());
    }

    private CompletionStage<AchievementProgress> saveInTransaction(
            TransactionContext transaction, AchievementProgress progress, long expectedRevision) {
        return transaction
                .queryOne(
                        selectSql(true),
                        binder -> {
                            binder.uuid(progress.playerId());
                            binder.string(progress.achievementId().value());
                        },
                        StorageAchievementRepository::mapRow)
                .thenCompose(existing -> {
                    if (existing.isPresent()) {
                        var current = existing.orElseThrow();
                        if (current.revision() != expectedRevision) {
                            return CompletableFuture.failedFuture(
                                    new AchievementProgressConflictException(current, expectedRevision));
                        }
                        return update(transaction, progress, expectedRevision)
                                .thenApply(ignored -> progress.withRevision(expectedRevision + 1));
                    }
                    if (expectedRevision != 0) {
                        var initial = AchievementProgress.initial(progress.playerId(), progress.achievementId());
                        return CompletableFuture.failedFuture(
                                new AchievementProgressConflictException(initial, expectedRevision));
                    }
                    return insert(transaction, progress)
                            .thenApply(ignored -> progress.withRevision(1))
                            .exceptionallyCompose(failure -> {
                                if (isUniqueConstraint(failure)) {
                                    return CompletableFuture.failedFuture(new AchievementProgressConflictException(
                                            AchievementProgress.initial(progress.playerId(), progress.achievementId()),
                                            expectedRevision));
                                }
                                return CompletableFuture.failedFuture(failure);
                            });
                });
    }

    private CompletionStage<Void> insert(TransactionContext transaction, AchievementProgress progress) {
        return transaction.update(
                "INSERT INTO " + TABLE
                        + " (player_id, achievement_id, unlocked, unlocked_at, reward_claim_id, revision) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                binder -> {
                    binder.uuid(progress.playerId());
                    binder.string(progress.achievementId().value());
                    bindValues(binder, progress, 1);
                });
    }

    private CompletionStage<Void> update(
            TransactionContext transaction, AchievementProgress progress, long expectedRevision) {
        return transaction.update(
                "UPDATE " + TABLE
                        + " SET unlocked = ?, unlocked_at = ?, reward_claim_id = ?, revision = ?"
                        + " WHERE player_id = ? AND achievement_id = ? AND revision = ?",
                binder -> {
                    bindValues(binder, progress, expectedRevision + 1);
                    binder.uuid(progress.playerId());
                    binder.string(progress.achievementId().value());
                    binder.longValue(expectedRevision);
                });
    }

    private static void bindValues(
            com.cotani.storage.query.ParameterBinder binder, AchievementProgress progress, long revision)
            throws SQLException {
        binder.set(progress.unlocked());
        binder.set(progress.unlockedAt().orElse(null));
        binder.set(progress.rewardClaimId()
                .map(RewardClaimId::value)
                .map(UUID::toString)
                .orElse(null));
        binder.longValue(revision);
    }

    private static AchievementProgress mapRow(Row row) throws SQLException {
        return new AchievementProgress(
                row.getUuidOptional("player_id").orElseThrow(),
                AchievementId.of(row.getString("achievement_id")),
                row.getBoolean("unlocked"),
                row.getInstantOptional("unlocked_at"),
                row.getStringOptional("reward_claim_id").map(UUID::fromString).map(RewardClaimId::new),
                row.getLong("revision"));
    }

    private String selectSql(boolean forUpdate) {
        return "SELECT player_id, achievement_id, unlocked, unlocked_at, reward_claim_id, revision FROM "
                + TABLE + " WHERE player_id = ? AND achievement_id = ?"
                + (forUpdate && !storage.dialect().name().equals("sqlite") ? " FOR UPDATE" : "");
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
