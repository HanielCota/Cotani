package com.cotani.ranking.internal;

import com.cotani.api.InternalApi;
import com.cotani.ranking.api.RankingDefinition;
import com.cotani.ranking.api.RankingEntry;
import com.cotani.ranking.api.RankingId;
import com.cotani.ranking.api.RankingNotFoundException;
import com.cotani.ranking.api.RankingService;
import com.cotani.ranking.api.RankingServiceOptions;
import com.cotani.ranking.api.RankingSnapshot;
import com.cotani.statistics.api.StatisticRanking;
import com.cotani.statistics.api.StatisticService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/** Default named ranking facade with bounded asynchronous query admission. */
@InternalApi
public final class DefaultRankingService implements RankingService {
    private static final Logger LOGGER = Logger.getLogger(DefaultRankingService.class.getName());

    private final StatisticService statistics;
    private final RankingServiceOptions options;
    private final Clock clock;
    private final Map<RankingId, RankingDefinition> definitions = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();
    private int pendingQueryCount;
    private @Nullable CompletableFuture<Void> closeStage;

    private DefaultRankingService(StatisticService statistics, RankingServiceOptions options, Clock clock) {
        this.statistics = Objects.requireNonNull(statistics, "statistics");
        this.options = Objects.requireNonNull(options, "options");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static DefaultRankingService create(
            StatisticService statistics, RankingServiceOptions options, Clock clock) {
        return new DefaultRankingService(statistics, options, clock);
    }

    @Override
    public void register(RankingDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        synchronized (lifecycleLock) {
            ensureOpen();
            var previous = definitions.putIfAbsent(definition.id(), definition);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Ranking is already registered: " + definition.id().value());
            }
        }
    }

    @Override
    public Optional<RankingDefinition> findDefinition(RankingId rankingId) {
        return Optional.ofNullable(definitions.get(Objects.requireNonNull(rankingId, "rankingId")));
    }

    @Override
    public CompletionStage<RankingSnapshot> topAsync(RankingId rankingId, int limit) {
        Objects.requireNonNull(rankingId, "rankingId");
        var definition = definitions.get(rankingId);
        if (definition == null) {
            return failed(new RankingNotFoundException(rankingId));
        }
        if (limit <= 0 || limit > definition.maxEntries()) {
            return failed(new IllegalArgumentException(
                    "limit must be between 1 and the ranking maxEntries (" + definition.maxEntries() + ")"));
        }
        if (!admitQuery()) {
            return failed(new RejectedExecutionException("Ranking query queue is full"));
        }

        CompletionStage<StatisticRanking> statisticStage;
        try {
            statisticStage = Objects.requireNonNull(
                    statistics.topAsync(definition.statisticId(), limit), "statistics returned null stage");
        } catch (RuntimeException failure) {
            releaseQuery();
            return failed(failure);
        }

        CompletionStage<RankingSnapshot> physicalStage;
        try {
            physicalStage = statisticStage.thenApply(entries -> toSnapshot(definition, entries));
        } catch (RuntimeException failure) {
            releaseQuery();
            return failed(failure);
        }
        physicalStage.whenComplete((ignored, failure) -> releaseQuery());
        try {
            return options.withQueryTimeout(physicalStage);
        } catch (RuntimeException failure) {
            return failed(failure);
        }
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        synchronized (lifecycleLock) {
            if (closeStage != null) {
                return closeStage;
            }
            closed.set(true);
            closeStage = new CompletableFuture<>();
            if (pendingQueryCount == 0) {
                closeStage.complete(null);
            }
            return closeStage;
        }
    }

    @Override
    public void close() {
        closeAsync().whenComplete((ignored, failure) -> {
            if (failure != null) {
                LOGGER.log(Level.SEVERE, "Failed to close ranking service", failure);
            }
        });
    }

    private boolean admitQuery() {
        synchronized (lifecycleLock) {
            if (closed.get() || pendingQueryCount >= options.maxPendingQueries()) {
                return false;
            }
            pendingQueryCount++;
            return true;
        }
    }

    private void releaseQuery() {
        synchronized (lifecycleLock) {
            pendingQueryCount--;
            if (pendingQueryCount == 0 && closed.get() && closeStage != null) {
                closeStage.complete(null);
            }
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Ranking service is closed");
        }
    }

    private RankingSnapshot toSnapshot(RankingDefinition definition, StatisticRanking ranking) {
        Objects.requireNonNull(ranking, "statistics returned null ranking");
        if (!ranking.statisticId().equals(definition.statisticId())) {
            throw new IllegalStateException("statistics returned a ranking for an unexpected statistic");
        }
        var entries = ranking.entries();
        var rankingEntries = new ArrayList<RankingEntry>(entries.size());
        for (var entry : entries) {
            rankingEntries.add(new RankingEntry(entry.rank(), entry.playerId(), entry.value()));
        }
        return new RankingSnapshot(definition, rankingEntries, clock.instant());
    }

    private static <T> CompletionStage<T> failed(Throwable failure) {
        return CompletableFuture.failedFuture(failure);
    }
}
