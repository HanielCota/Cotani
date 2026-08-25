package com.cotani.quest.internal;

import com.cotani.api.InternalApi;
import com.cotani.quest.api.QuestId;
import com.cotani.quest.api.QuestProgress;
import com.cotani.quest.api.QuestProgressConflictException;
import com.cotani.quest.api.QuestRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Thread-safe in-memory quest repository for tests and ephemeral servers. */
@InternalApi
public final class InMemoryQuestRepository implements QuestRepository {
    private final Map<Key, QuestProgress> progress = new HashMap<>();

    @Override
    public synchronized CompletionStage<Optional<QuestProgress>> findAsync(UUID playerId, QuestId questId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(questId, "questId");
        return CompletableFuture.completedFuture(Optional.ofNullable(progress.get(new Key(playerId, questId))));
    }

    @Override
    public synchronized CompletionStage<QuestProgress> saveAsync(QuestProgress next, long expectedRevision) {
        Objects.requireNonNull(next, "next");
        if (next.revision() < 0) {
            throw new IllegalArgumentException("progress revision cannot be negative");
        }
        if (expectedRevision < 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("expectedRevision cannot be negative"));
        }

        var key = new Key(next.playerId(), next.questId());
        var previous = progress.get(key);
        var actualRevision = previous == null ? 0 : previous.revision();
        if (actualRevision != expectedRevision) {
            var actual = previous == null ? QuestProgress.initial(next.playerId(), next.questId()) : previous;
            return CompletableFuture.failedFuture(new QuestProgressConflictException(actual, expectedRevision));
        }

        var saved = next.withRevision(expectedRevision + 1);
        progress.put(key, saved);
        return CompletableFuture.completedFuture(saved);
    }

    private record Key(UUID playerId, QuestId questId) {}
}
