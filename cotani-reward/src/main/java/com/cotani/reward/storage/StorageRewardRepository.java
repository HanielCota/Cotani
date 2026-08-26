package com.cotani.reward.storage;

import com.cotani.reward.api.RewardClaim;
import com.cotani.reward.api.RewardClaimCommand;
import com.cotani.reward.api.RewardClaimConflictException;
import com.cotani.reward.api.RewardClaimId;
import com.cotani.reward.api.RewardId;
import com.cotani.reward.api.RewardOnCooldownException;
import com.cotani.reward.api.RewardRepository;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import com.cotani.storage.query.Row;
import com.cotani.storage.transaction.TransactionContext;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import org.jspecify.annotations.Nullable;

/** SQL-backed repository with atomic cooldown, streak and idempotency handling. */
public final class StorageRewardRepository implements RewardRepository {
    private static final String CLAIMS = "cotani_rewards_claims";
    private static final String STATES = "cotani_rewards_states";
    private final CotaniStorage storage;
    private final @Nullable Clock timeOverride;

    public StorageRewardRepository(CotaniStorage storage) {
        this(storage, null);
    }

    StorageRewardRepository(CotaniStorage storage, @Nullable Clock timeOverride) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.timeOverride = timeOverride;
    }

    @Override
    public CompletionStage<RewardClaim> claimAsync(RewardClaimCommand command) {
        Objects.requireNonNull(command, "command");
        var transaction = storage.transactions().runAsync(context -> claimInTransaction(context, command));
        return transaction
                .handle((claim, failure) -> new TransactionResult(claim, failure))
                .thenCompose(result -> {
                    if (result.failure() == null) {
                        return CompletableFuture.completedFuture(result.claim());
                    }
                    var failure = unwrap(result.failure());
                    if (!isUniqueViolation(failure)) {
                        return CompletableFuture.failedFuture(failure);
                    }
                    return findAsync(command.claimId().value()).thenCompose(existing -> {
                        if (existing.isEmpty()) {
                            return CompletableFuture.failedFuture(failure);
                        }
                        var claim = existing.orElseThrow();
                        if (!claim.playerId().equals(command.playerId())
                                || !claim.rewardId().equals(command.definition().id())) {
                            return CompletableFuture.failedFuture(new RewardClaimConflictException(command.claimId()));
                        }
                        return CompletableFuture.completedFuture(claim);
                    });
                });
    }

    @Override
    public CompletionStage<Void> purgeClaimsBeforeAsync(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        return storage.queryExecutor()
                .update("DELETE FROM " + CLAIMS + " WHERE settled = ? AND claimed_at < ?", binder -> {
                    binder.set(true);
                    binder.instant(cutoff);
                });
    }

    @Override
    public CompletionStage<List<RewardClaim>> pendingClaimsAsync(int limit) {
        validateLimit(limit);
        return storage.queryExecutor()
                .queryMany(
                        "SELECT claim_id, player_id, reward_id, claimed_at, next_available_at, streak, total_claims, grants FROM "
                                + CLAIMS
                                + " WHERE settled = ? ORDER BY claimed_at ASC, claim_id ASC LIMIT ?",
                        binder -> {
                            binder.set(false);
                            binder.integer(limit);
                        },
                        StorageRewardRepository::mapClaim);
    }

    @Override
    public CompletionStage<Optional<RewardClaim>> findPendingClaimAsync(UUID playerId, RewardId rewardId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(rewardId, "rewardId");
        return storage.queryExecutor()
                .queryOne(
                        "SELECT claim_id, player_id, reward_id, claimed_at, next_available_at, streak, total_claims, grants FROM "
                                + CLAIMS
                                + " WHERE settled = ? AND player_id = ? AND reward_id = ?"
                                + " ORDER BY claimed_at ASC, claim_id ASC LIMIT 1",
                        binder -> {
                            binder.set(false);
                            binder.uuid(playerId);
                            binder.string(rewardId.value());
                        },
                        StorageRewardRepository::mapClaim);
    }

    @Override
    public CompletionStage<Boolean> markSettledAsync(RewardClaimId claimId) {
        Objects.requireNonNull(claimId, "claimId");
        return findAsync(claimId.value()).thenCompose(existing -> {
            if (existing.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }
            return storage.queryExecutor()
                    .update("UPDATE " + CLAIMS + " SET settled = ? WHERE claim_id = ?", binder -> {
                        binder.set(true);
                        binder.uuid(claimId.value());
                    })
                    .thenApply(ignored -> true);
        });
    }

    public static List<Migration> migrations() {
        return List.of(
                new CreateRewardTablesMigration(),
                new CreateRewardIndexesMigration(),
                new MigrateRewardSettlementMigration());
    }

    private CompletionStage<RewardClaim> claimInTransaction(
            TransactionContext transaction, RewardClaimCommand command) {
        return currentTime(transaction)
                .thenCompose(now -> claimAt(
                        transaction,
                        new RewardClaimCommand(command.claimId(), command.playerId(), command.definition(), now)));
    }

    private CompletionStage<RewardClaim> claimAt(TransactionContext transaction, RewardClaimCommand command) {
        var definition = command.definition();
        return ensureStateRow(transaction, command)
                .thenCompose(ignored -> transaction.queryOne(
                        claimSelectSql(),
                        binder -> binder.uuid(command.claimId().value()),
                        StorageRewardRepository::mapClaim))
                .thenCompose(existing -> {
                    if (existing.isPresent()) {
                        return sameClaimOrConflict(existing.orElseThrow(), command);
                    }
                    return transaction
                            .queryOne(
                                    stateSelectSql(),
                                    binder -> {
                                        binder.uuid(command.playerId());
                                        binder.string(definition.id().value());
                                    },
                                    StorageRewardRepository::mapState)
                            .thenCompose(state -> {
                                var previous = state.orElseThrow();
                                var availableAt = previous.lastClaimAt().plus(definition.cooldown());
                                if (command.now().isBefore(availableAt)) {
                                    return CompletableFuture.failedFuture(new RewardOnCooldownException(
                                            command.playerId(), definition.id(), availableAt));
                                }
                                var claim = createClaim(command, previous);
                                return insertClaim(transaction, claim)
                                        .thenCompose(
                                                ignored -> updateState(transaction, claim, previous.revision() + 1))
                                        .thenApply(ignored -> claim);
                            });
                });
    }

    private CompletionStage<Void> ensureStateRow(TransactionContext transaction, RewardClaimCommand command) {
        var sql = storage.dialect()
                .upsert(
                        STATES,
                        List.of("player_id", "reward_id", "last_claim_at", "streak", "total_claims", "revision"),
                        List.of("player_id", "reward_id"),
                        List.of());
        return transaction.update(sql, binder -> {
            binder.uuid(command.playerId());
            binder.string(command.definition().id().value());
            binder.instant(Instant.EPOCH);
            binder.integer(0);
            binder.longValue(0L);
            binder.longValue(0L);
        });
    }

    private CompletionStage<Void> insertClaim(TransactionContext transaction, RewardClaim claim) {
        return transaction.update(
                "INSERT INTO " + CLAIMS
                        + " (claim_id, player_id, reward_id, claimed_at, next_available_at, streak, total_claims, grants, settled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                binder -> {
                    binder.uuid(claim.claimId().value());
                    binder.uuid(claim.playerId());
                    binder.string(claim.rewardId().value());
                    binder.instant(claim.claimedAt());
                    binder.instant(claim.nextAvailableAt());
                    binder.integer(claim.streak());
                    binder.longValue(claim.totalClaims());
                    binder.string(RewardGrantCodec.encode(claim.grants()));
                    binder.set(false);
                });
    }

    private CompletionStage<Instant> currentTime(TransactionContext transaction) {
        if (timeOverride != null) {
            return CompletableFuture.completedFuture(timeOverride.instant());
        }
        return transaction
                .queryOne(
                        "SELECT " + storage.dialect().currentTimestamp() + " AS reward_now",
                        _ -> {},
                        row -> row.getInstantOptional("reward_now").orElseThrow())
                .thenApply(Optional::orElseThrow);
    }

    private CompletionStage<Void> updateState(TransactionContext transaction, RewardClaim claim, long revision) {
        var sql = "UPDATE " + STATES
                + " SET last_claim_at = ?, streak = ?, total_claims = ?, revision = ? WHERE player_id = ? AND reward_id = ?";
        return transaction.update(sql, binder -> {
            binder.instant(claim.claimedAt());
            binder.integer(claim.streak());
            binder.longValue(claim.totalClaims());
            binder.longValue(revision);
            binder.uuid(claim.playerId());
            binder.string(claim.rewardId().value());
        });
    }

    private static RewardClaim createClaim(RewardClaimCommand command, StoredState previous) {
        var definition = command.definition();
        var streak = command.now().isAfter(previous.lastClaimAt().plus(definition.streakWindow()))
                ? 1
                : previous.streak() >= definition.maxStreak() ? definition.maxStreak() : previous.streak() + 1;
        var totalClaims = Math.addExact(previous.totalClaims(), 1L);
        return new RewardClaim(
                command.claimId(),
                command.playerId(),
                definition.id(),
                command.now(),
                command.now().plus(definition.cooldown()),
                streak,
                totalClaims,
                definition.grants());
    }

    private CompletionStage<RewardClaim> sameClaimOrConflict(RewardClaim existing, RewardClaimCommand command) {
        if (!existing.playerId().equals(command.playerId())
                || !existing.rewardId().equals(command.definition().id())) {
            return CompletableFuture.failedFuture(new RewardClaimConflictException(command.claimId()));
        }
        return CompletableFuture.completedFuture(existing);
    }

    private CompletionStage<Optional<RewardClaim>> findAsync(UUID claimId) {
        return storage.queryExecutor()
                .queryOne(claimSelectSql(), binder -> binder.uuid(claimId), StorageRewardRepository::mapClaim);
    }

    private static String claimSelectSql() {
        return "SELECT claim_id, player_id, reward_id, claimed_at, next_available_at, streak, total_claims, grants FROM "
                + CLAIMS + " WHERE claim_id = ?";
    }

    private String stateSelectSql() {
        var suffix = storage.dialect().name().equals("sqlite") ? "" : " FOR UPDATE";
        return "SELECT player_id, reward_id, last_claim_at, streak, total_claims, revision FROM " + STATES
                + " WHERE player_id = ? AND reward_id = ?" + suffix;
    }

    private static StoredState mapState(Row row) throws SQLException {
        return new StoredState(
                row.getInstantOptional("last_claim_at").orElseThrow(),
                row.getInt("streak"),
                row.getLong("total_claims"),
                row.getLong("revision"));
    }

    private static RewardClaim mapClaim(Row row) throws SQLException {
        return new RewardClaim(
                new com.cotani.reward.api.RewardClaimId(UUID.fromString(row.getString("claim_id"))),
                row.getUuidOptional("player_id").orElseThrow(),
                com.cotani.reward.api.RewardId.of(row.getString("reward_id")),
                row.getInstantOptional("claimed_at").orElseThrow(),
                row.getInstantOptional("next_available_at").orElseThrow(),
                row.getInt("streak"),
                row.getLong("total_claims"),
                RewardGrantCodec.decode(row.getString("grants")));
    }

    private static Throwable unwrap(Throwable failure) {
        var current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static boolean isUniqueViolation(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                var state = sqlException.getSQLState();
                if (state != null && state.startsWith("23")) {
                    return true;
                }
            }
            var message = current.getMessage();
            if (message != null) {
                var normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("unique constraint")
                        || normalized.contains("duplicate entry")
                        || normalized.contains("primary key")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static void validateLimit(int limit) {
        if (limit <= 0 || limit > 1_000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
    }

    private record StoredState(Instant lastClaimAt, int streak, long totalClaims, long revision) {}

    private record TransactionResult(RewardClaim claim, Throwable failure) {}
}
