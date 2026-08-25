package com.cotani.achievement.internal;

import com.cotani.achievement.api.AchievementId;
import com.cotani.achievement.api.AchievementProgress;
import com.cotani.achievement.api.AchievementProgressConflictException;
import com.cotani.achievement.api.AchievementRepository;
import com.cotani.api.InternalApi;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Thread-safe in-memory achievement repository for tests and ephemeral servers. */
@InternalApi
public final class InMemoryAchievementRepository implements AchievementRepository {
    private final Map<Key, AchievementProgress> progress = new HashMap<>();

    @Override
    public synchronized CompletionStage<Optional<AchievementProgress>> findAsync(
            UUID playerId, AchievementId achievementId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(achievementId, "achievementId");
        return CompletableFuture.completedFuture(Optional.ofNullable(progress.get(new Key(playerId, achievementId))));
    }

    @Override
    public synchronized CompletionStage<AchievementProgress> saveAsync(
            AchievementProgress next, long expectedRevision) {
        Objects.requireNonNull(next, "next");
        if (expectedRevision < 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("expectedRevision cannot be negative"));
        }
        var key = new Key(next.playerId(), next.achievementId());
        var current = progress.get(key);
        if (current == null && expectedRevision != 0) {
            return CompletableFuture.failedFuture(new AchievementProgressConflictException(
                    AchievementProgress.initial(next.playerId(), next.achievementId()), expectedRevision));
        }
        if (current != null && current.revision() != expectedRevision) {
            return CompletableFuture.failedFuture(new AchievementProgressConflictException(current, expectedRevision));
        }
        var saved = next.withRevision(expectedRevision + 1);
        progress.put(key, saved);
        return CompletableFuture.completedFuture(saved);
    }

    private record Key(UUID playerId, AchievementId achievementId) {}
}
