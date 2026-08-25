package com.cotani.season.internal;

import com.cotani.api.InternalApi;
import com.cotani.season.api.SeasonExperienceCommand;
import com.cotani.season.api.SeasonExperienceConflictException;
import com.cotani.season.api.SeasonExperienceId;
import com.cotani.season.api.SeasonId;
import com.cotani.season.api.SeasonProgress;
import com.cotani.season.api.SeasonProgressConflictException;
import com.cotani.season.api.SeasonRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Thread-safe in-memory season repository for tests and ephemeral servers. */
@InternalApi
public final class InMemorySeasonRepository implements SeasonRepository {
    private final Map<Key, SeasonProgress> progress = new HashMap<>();
    private final Map<SeasonExperienceId, AppliedOperation> operations = new HashMap<>();

    @Override
    public synchronized CompletionStage<Optional<SeasonProgress>> findAsync(UUID playerId, SeasonId seasonId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(seasonId, "seasonId");
        return CompletableFuture.completedFuture(Optional.ofNullable(progress.get(new Key(playerId, seasonId))));
    }

    @Override
    public synchronized CompletionStage<SeasonProgress> applyExperienceAsync(SeasonExperienceCommand command) {
        Objects.requireNonNull(command, "command");
        var previousOperation = operations.get(command.operationId());
        if (previousOperation != null) {
            if (!previousOperation.matches(command)) {
                return CompletableFuture.failedFuture(new SeasonExperienceConflictException(command.operationId()));
            }
            return CompletableFuture.completedFuture(previousOperation.result());
        }

        var key = new Key(command.playerId(), command.seasonId());
        var current = progress.getOrDefault(key, SeasonProgress.initial(command.playerId(), command.seasonId()));
        final long experience;
        try {
            experience = Math.addExact(current.experience(), command.amount());
        } catch (ArithmeticException overflow) {
            return CompletableFuture.failedFuture(overflow);
        }
        var saved = new SeasonProgress(
                current.playerId(), current.seasonId(), experience, current.claimedLevels(), current.revision() + 1);
        progress.put(key, saved);
        operations.put(command.operationId(), new AppliedOperation(command, saved));
        return CompletableFuture.completedFuture(saved);
    }

    @Override
    public synchronized CompletionStage<Void> purgeExperienceOperationsBeforeAsync(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        operations
                .entrySet()
                .removeIf(entry -> entry.getValue().command().occurredAt().isBefore(cutoff));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletionStage<SeasonProgress> saveAsync(SeasonProgress next, long expectedRevision) {
        Objects.requireNonNull(next, "next");
        if (expectedRevision < 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("expectedRevision cannot be negative"));
        }
        var key = new Key(next.playerId(), next.seasonId());
        var previous = progress.get(key);
        var actualRevision = previous == null ? 0 : previous.revision();
        if (actualRevision != expectedRevision) {
            var actual = previous == null ? SeasonProgress.initial(next.playerId(), next.seasonId()) : previous;
            return CompletableFuture.failedFuture(new SeasonProgressConflictException(actual, expectedRevision));
        }
        var saved = next.withRevision(expectedRevision + 1);
        progress.put(key, saved);
        return CompletableFuture.completedFuture(saved);
    }

    private record Key(UUID playerId, SeasonId seasonId) {}

    private record AppliedOperation(SeasonExperienceCommand command, SeasonProgress result) {
        private boolean matches(SeasonExperienceCommand other) {
            return command.playerId().equals(other.playerId())
                    && command.seasonId().equals(other.seasonId())
                    && command.amount() == other.amount();
        }
    }
}
