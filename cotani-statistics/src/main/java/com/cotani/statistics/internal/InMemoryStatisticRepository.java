package com.cotani.statistics.internal;

import com.cotani.api.InternalApi;
import com.cotani.statistics.api.StatisticEntry;
import com.cotani.statistics.api.StatisticId;
import com.cotani.statistics.api.StatisticOperationId;
import com.cotani.statistics.api.StatisticOverflowException;
import com.cotani.statistics.api.StatisticRankEntry;
import com.cotani.statistics.api.StatisticRepository;
import com.cotani.statistics.api.StatisticUpdate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Thread-safe in-memory statistic repository. */
@InternalApi
public final class InMemoryStatisticRepository implements StatisticRepository {
    private final Map<Key, StatisticEntry> entries = new HashMap<>();
    private final Map<StatisticOperationId, AppliedOperation> operations = new HashMap<>();

    @Override
    public synchronized CompletionStage<Optional<StatisticEntry>> findAsync(UUID playerId, StatisticId statisticId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(statisticId, "statisticId");
        return CompletableFuture.completedFuture(Optional.ofNullable(entries.get(new Key(playerId, statisticId))));
    }

    @Override
    public synchronized CompletionStage<StatisticUpdate> incrementAsync(
            UUID playerId, StatisticId statisticId, long amount, Instant updatedAt) {
        return incrementInternal(playerId, statisticId, amount, updatedAt, null);
    }

    @Override
    public synchronized CompletionStage<StatisticUpdate> incrementIdempotentlyAsync(
            UUID playerId, StatisticId statisticId, long amount, Instant updatedAt, StatisticOperationId operationId) {
        Objects.requireNonNull(operationId, "operationId");
        return incrementInternal(playerId, statisticId, amount, updatedAt, operationId);
    }

    private CompletionStage<StatisticUpdate> incrementInternal(
            UUID playerId,
            StatisticId statisticId,
            long amount,
            Instant updatedAt,
            @org.jspecify.annotations.Nullable StatisticOperationId operationId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(statisticId, "statisticId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (amount <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("amount must be positive"));
        }
        var appliedOperation = operationId == null ? null : operations.get(operationId);
        if (appliedOperation != null) {
            if (!appliedOperation.matches(playerId, statisticId, amount)) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("operation id was already used for another increment"));
            }
            var update = appliedOperation.update();
            return CompletableFuture.completedFuture(
                    new StatisticUpdate(update.amount(), update.previousValue(), update.current(), false));
        }
        var key = new Key(playerId, statisticId);
        var previous = entries.getOrDefault(key, StatisticEntry.initial(playerId, statisticId));
        final long value;
        final long revision;
        try {
            value = Math.addExact(previous.value(), amount);
            revision = Math.addExact(previous.revision(), 1L);
        } catch (ArithmeticException overflow) {
            return CompletableFuture.failedFuture(new StatisticOverflowException(playerId, statisticId));
        }
        var current = new StatisticEntry(playerId, statisticId, value, updatedAt, revision);
        entries.put(key, current);
        var update = new StatisticUpdate(amount, previous.value(), current);
        if (operationId != null) {
            operations.put(operationId, new AppliedOperation(playerId, statisticId, amount, update));
        }
        return CompletableFuture.completedFuture(update);
    }

    @Override
    public synchronized CompletionStage<List<StatisticRankEntry>> topAsync(StatisticId statisticId, int limit) {
        Objects.requireNonNull(statisticId, "statisticId");
        if (limit <= 0 || limit > 1_000) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("limit must be between 1 and 1000"));
        }
        var ranked = entries.entrySet().stream()
                .filter(entry -> entry.getKey().statisticId().equals(statisticId))
                .sorted(Map.Entry.<Key, StatisticEntry>comparingByValue(Comparator.comparingLong(StatisticEntry::value)
                        .reversed()
                        .thenComparing(entry -> entry.playerId().toString())))
                .limit(limit)
                .map(entry -> entry.getValue())
                .toList();
        var result = new ArrayList<StatisticRankEntry>(ranked.size());
        for (int index = 0; index < ranked.size(); index++) {
            result.add(new StatisticRankEntry(
                    index + 1, ranked.get(index).playerId(), ranked.get(index).value()));
        }
        return CompletableFuture.completedFuture(List.copyOf(result));
    }

    private record Key(UUID playerId, StatisticId statisticId) {}

    private record AppliedOperation(UUID playerId, StatisticId statisticId, long amount, StatisticUpdate update) {
        private boolean matches(UUID expectedPlayerId, StatisticId expectedStatisticId, long expectedAmount) {
            return playerId.equals(expectedPlayerId)
                    && statisticId.equals(expectedStatisticId)
                    && amount == expectedAmount;
        }
    }
}
