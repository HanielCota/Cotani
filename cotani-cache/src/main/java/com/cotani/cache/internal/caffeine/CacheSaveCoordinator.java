package com.cotani.cache.internal.caffeine;

import com.cotani.cache.exception.CacheSaveException;
import com.cotani.cache.invalidation.CacheInvalidation;
import com.cotani.cache.invalidation.CacheInvalidationBus;
import com.cotani.cache.repository.CacheRepository;
import com.cotani.task.util.CompletionStages;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

final class CacheSaveCoordinator<K, V> {
    private static final Logger LOGGER = Logger.getLogger(CacheSaveCoordinator.class.getName());
    private static final String REPOSITORY_SAVE_NULL_MSG = "repository.save returned null";
    private static final String VALUE_PARAM = "value";

    private final CacheRepository<K, V> repository;
    private final CacheInvalidationBus<K> invalidationBus;
    private final UUID cacheId;
    private final int maximumConcurrency;
    private final ConcurrentHashMap<K, PendingSave<V>> pendingSaves = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<K, SaveLane> saveLanes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SaveOrder, CompletableFuture<Void>> evictionWork = new ConcurrentHashMap<>();

    CacheSaveCoordinator(
            CacheRepository<K, V> repository,
            CacheInvalidationBus<K> invalidationBus,
            UUID cacheId,
            int maximumConcurrency) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.invalidationBus = Objects.requireNonNull(invalidationBus, "invalidationBus");
        this.cacheId = Objects.requireNonNull(cacheId, "cacheId");

        if (maximumConcurrency <= 0) {
            throw new IllegalArgumentException("maximumConcurrency must be positive");
        }

        this.maximumConcurrency = maximumConcurrency;
    }

    CompletionStage<Void> persist(K key, V value, SaveOrder order) {
        var ticketRef = new AtomicReference<SaveTicket>();
        var laneRef = new AtomicReference<SaveLane>();
        saveLanes.compute(key, (_, current) -> {
            var lane = current == null ? new SaveLane(key) : current;
            laneRef.set(lane);
            ticketRef.set(lane.enqueue(value, order));

            return lane;
        });

        var ticket = Objects.requireNonNull(ticketRef.get(), "save ticket");
        var result = ticket.result();
        var lane = Objects.requireNonNull(laneRef.get(), "save lane");
        var _ = result.whenComplete((_, _) -> saveLanes.computeIfPresent(
                key, (_, current) -> current.equals(lane) && current.isIdle(ticket.sequence()) ? null : current));

        return result;
    }

    void queue(K key, V value, SaveOrder order) {
        var candidate = new PendingSave<>(value, order);
        pendingSaves.compute(
                key,
                (_, current) ->
                        current == null || candidate.order().compareTo(current.order()) > 0 ? candidate : current);
    }

    void saveEvicted(K key, V value, SaveOrder order) {
        var work = new CompletableFuture<Void>();
        evictionWork.put(order, work);
        var _ = persist(key, value, order).whenComplete((_, error) -> {
            if (error != null) {
                LOGGER.log(
                        Level.SEVERE,
                        error,
                        () -> "Could not save evicted cache entry: " + key + ". Queuing for retry.");
                queue(key, value, order);
            }
            work.complete(null);
            evictionWork.remove(order, work);
        });
    }

    CompletionStage<Void> savePending() {
        if (pendingSaves.isEmpty()) {
            return CompletionStages.completedVoid();
        }

        var entries = Map.copyOf(pendingSaves);

        return runBounded(List.copyOf(entries.entrySet()), entry -> savePendingEntry(entry.getKey(), entry.getValue()));
    }

    <T> CompletionStage<Void> runBounded(List<T> items, Function<T, CompletionStage<Void>> operation) {
        return new BoundedAsyncWorkCoordinator<>(items, maximumConcurrency, operation).start();
    }

    CompletionStage<Void> awaitSaveLanes() {
        return allOf(saveLanes.values().stream().map(SaveLane::tail));
    }

    CompletionStage<Void> awaitEvictionWork() {
        return allOf(List.copyOf(evictionWork.values()).stream());
    }

    private CompletionStage<Void> savePendingEntry(K key, PendingSave<V> pending) {
        return persist(key, pending.value(), pending.order())
                .thenRun(() -> pendingSaves.remove(key, pending))
                .exceptionallyCompose(error -> {
                    LOGGER.log(
                            Level.SEVERE,
                            error,
                            () -> "Could not save pending cache entry: " + key + ". Re-queueing for retry.");

                    return CompletableFuture.failedFuture(
                            new CacheSaveException("Could not save pending cache entry: " + key, error));
                })
                .toCompletableFuture();
    }

    private static CompletionStage<Void> allOf(Stream<? extends CompletionStage<Void>> stages) {
        var array = stages.map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new);
        return array.length == 0 ? CompletionStages.completedVoid() : CompletableFuture.allOf(array);
    }

    private final class SaveLane {
        private final K key;
        private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
        private SaveOrder newestOrder = SaveOrder.NONE;
        private long tailSequence;

        private SaveLane(K key) {
            this.key = key;
        }

        synchronized SaveTicket enqueue(V value, SaveOrder order) {
            if (order.compareTo(newestOrder) > 0) {
                newestOrder = order;
            }
            tail = tail.handle((_, _) -> null)
                    .thenCompose(_ -> {
                        synchronized (this) {
                            if (order.compareTo(newestOrder) < 0) {
                                return CompletionStages.completedVoid();
                            }
                        }

                        return Objects.requireNonNull(repository.save(key, value), REPOSITORY_SAVE_NULL_MSG)
                                .thenCompose(_ -> invalidationBus.publish(new CacheInvalidation<>(cacheId, key)));
                    })
                    .toCompletableFuture();
            tailSequence++;

            return new SaveTicket(tail, tailSequence);
        }

        synchronized CompletableFuture<Void> tail() {
            return tail;
        }

        synchronized boolean isIdle(long completedSequence) {
            return tailSequence == completedSequence && tail.isDone();
        }
    }

    private record PendingSave<V>(V value, SaveOrder order) {
        PendingSave {
            Objects.requireNonNull(value, VALUE_PARAM);
            Objects.requireNonNull(order, "order");
        }
    }

    private record SaveTicket(CompletableFuture<Void> result, long sequence) {}
}
