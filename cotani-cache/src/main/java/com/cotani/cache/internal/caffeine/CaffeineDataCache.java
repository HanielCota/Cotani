package com.cotani.cache.internal.caffeine;

import com.cotani.cache.api.DataCache;
import com.cotani.cache.entry.CacheEntry;
import com.cotani.cache.exception.CacheException;
import com.cotani.cache.exception.CacheLoadException;
import com.cotani.cache.exception.CacheSaveException;
import com.cotani.cache.invalidation.CacheInvalidation;
import com.cotani.cache.invalidation.CacheInvalidationBus;
import com.cotani.cache.invalidation.CacheInvalidationSubscription;
import com.cotani.cache.invalidation.NoopCacheInvalidationBus;
import com.cotani.cache.policy.CacheSettings;
import com.cotani.cache.repository.CacheRepository;
import com.cotani.cache.stats.CacheStatsView;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import com.cotani.task.util.CompletionStages;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.jspecify.annotations.Nullable;

/**
 * Caffeine-backed implementation of {@link DataCache}.
 *
 * <p>Uses {@link AsyncLoadingCache} for automatic loading and eviction.
 * Dirty entries are tracked via an atomic counter for O(1) counting.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
@com.cotani.api.InternalApi
public final class CaffeineDataCache<K, V> implements DataCache<K, V> {

    private static final Logger LOGGER = Logger.getLogger(CaffeineDataCache.class.getName());
    private static final String REPOSITORY_SAVE_NULL_MSG = "repository.save returned null";

    private final AsyncLoadingCache<K, CacheEntry<V>> cache;
    private final CacheRepository<K, V> repository;
    private final Function<K, V> defaultValue;
    private final PaperTaskScheduler scheduler;
    private final CacheSettings settings;
    private final int maximumConcurrentSaves;
    private final UUID cacheId = UUID.randomUUID();
    private final CacheInvalidationBus<K> invalidationBus;
    private final CacheInvalidationSubscription invalidationSubscription;
    private final SchedulerTask autosaveTask;
    private final TrackedExecutor cacheExecutor;
    private final ConcurrentHashMap<K, PendingSave<V>> pendingSaves = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<K, CacheEntry<V>> dirtyEntries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CacheEntry<V>, Long> entryGenerations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<K, SaveLane> saveLanes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SaveOrder, CompletableFuture<Void>> evictionWork = new ConcurrentHashMap<>();
    private final AtomicLong nextGeneration = new AtomicLong();
    private final AtomicBoolean autosaveInProgress = new AtomicBoolean(false);
    private final Object closeLock = new Object();
    private volatile boolean closing;
    private @Nullable CompletableFuture<Void> closeFuture;

    public CaffeineDataCache(
            CacheRepository<K, V> repository,
            Function<K, V> defaultValue,
            PaperTaskScheduler scheduler,
            CacheSettings settings) {
        this(repository, defaultValue, scheduler, settings, 16, new NoopCacheInvalidationBus<>());
    }

    public CaffeineDataCache(
            CacheRepository<K, V> repository,
            Function<K, V> defaultValue,
            PaperTaskScheduler scheduler,
            CacheSettings settings,
            int maximumConcurrentSaves,
            CacheInvalidationBus<K> invalidationBus) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.settings = Objects.requireNonNull(settings, "settings");
        if (maximumConcurrentSaves <= 0) {
            throw new IllegalArgumentException("maximumConcurrentSaves must be positive");
        }
        this.maximumConcurrentSaves = maximumConcurrentSaves;
        this.invalidationBus = Objects.requireNonNull(invalidationBus, "invalidationBus");
        this.cacheExecutor = new TrackedExecutor(scheduler.asyncExecutor());
        this.cache = createCache(settings);
        this.invalidationSubscription = invalidationBus.subscribe(this::onInvalidation);
        this.autosaveTask = startAutosave(settings);
    }

    private static CompletionStage<Void> allOf(Stream<? extends CompletionStage<Void>> stages) {
        var array = stages.map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new);
        return array.length == 0 ? CompletionStages.completedVoid() : CompletableFuture.allOf(array);
    }

    @Override
    public V get(K key) {
        ensureOpen();
        return find(key)
                .orElseThrow(() -> new CacheException(
                        "Cache entry is not loaded: " + key + ". Use getOrLoad(key) or load(key) first."));
    }

    @Override
    public Optional<V> find(K key) {
        ensureOpen();
        return Optional.ofNullable(cache.synchronous().getIfPresent(key)).map(CacheEntry::value);
    }

    @Override
    public CompletionStage<V> getOrLoad(K key) {
        ensureOpen();
        return cache.get(key).thenApply(CacheEntry::value);
    }

    @Override
    public CompletionStage<V> load(K key) {
        ensureOpen();
        cache.synchronous().invalidate(key);
        return getOrLoad(key);
    }

    @Override
    public CompletionStage<V> update(K key, UnaryOperator<V> updater) {
        ensureOpen();
        var entry = getRequiredEntry(key);
        synchronized (entry) {
            if (entry.update(updater)) {
                dirtyEntries.put(key, entry);
            }
        }
        return CompletableFuture.completedFuture(entry.value());
    }

    @Override
    public CompletionStage<V> mutate(K key, Consumer<V> mutator) {
        ensureOpen();
        var entry = getRequiredEntry(key);
        synchronized (entry) {
            if (entry.mutate(mutator)) {
                dirtyEntries.put(key, entry);
            }
        }
        return CompletableFuture.completedFuture(entry.value());
    }

    @Override
    public void put(K key, V value) {
        ensureOpen();
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        CacheEntry<V> previous = cache.synchronous().getIfPresent(key);
        if (previous != null) {
            synchronized (previous) {
                if (previous.dirty()) {
                    // Capture dirty value before replace; mark clean so onRemoval cannot save it twice.
                    enqueuePendingSave(key, previous);
                    previous.markSavedIfVersionMatches(previous.version());
                    dirtyEntries.remove(key, previous);
                }
            }
        }
        cache.synchronous().put(key, createEntry(value));
    }

    @Override
    public CompletionStage<Void> save(K key) {
        ensureOpen();
        return saveEntry(key);
    }

    private CompletionStage<Void> saveEntry(K key) {
        return Optional.ofNullable(cache.synchronous().getIfPresent(key))
                .map(entry -> {
                    final SaveSnapshot<V> snapshot;
                    synchronized (entry) {
                        snapshot =
                                new SaveSnapshot<>(entry.value(), new SaveOrder(generationOf(entry), entry.version()));
                    }
                    return persist(key, snapshot.value(), snapshot.order())
                            .thenRun(() -> {
                                synchronized (entry) {
                                    if (entry.markSavedIfVersionMatches(
                                            snapshot.order().version())) {
                                        dirtyEntries.remove(key, entry);
                                    }
                                }
                            })
                            .exceptionallyCompose(error -> {
                                throw new CacheSaveException("Could not save cache entry: " + key, error);
                            });
                })
                .orElseGet(CompletionStages::completedVoid);
    }

    @Override
    public CompletionStage<Void> saveDirty() {
        ensureOpen();
        return saveDirtyEntries();
    }

    private CompletionStage<Void> saveDirtyEntries() {
        var keys = List.copyOf(dirtyEntries.keySet());
        return runBounded(keys, this::saveEntry);
    }

    @Override
    public CompletionStage<Void> saveAll() {
        ensureOpen();
        return runBounded(List.copyOf(cache.synchronous().asMap().keySet()), this::saveEntry);
    }

    @Override
    public void unload(K key) {
        ensureOpen();
        cache.synchronous().invalidate(key);
    }

    @Override
    public boolean contains(K key) {
        return cache.synchronous().getIfPresent(key) != null;
    }

    @Override
    public void markDirty(K key) {
        ensureOpen();
        CacheEntry<V> entry = getRequiredEntry(key);
        synchronized (entry) {
            if (entry.markDirty()) {
                dirtyEntries.put(key, entry);
            }
        }
    }

    @Override
    public int dirtyCount() {
        return dirtyEntries.size();
    }

    @Override
    public long size() {
        return cache.synchronous().estimatedSize();
    }

    @Override
    public Map<K, V> snapshot() {
        return Map.copyOf(cache.synchronous().asMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().value())));
    }

    @Override
    public CacheStatsView stats() {
        var stats = cache.synchronous().stats();

        return new CacheStatsView(
                cache.synchronous().estimatedSize(),
                stats.hitCount(),
                stats.missCount(),
                stats.hitRate(),
                stats.evictionCount(),
                dirtyEntries.size());
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        synchronized (closeLock) {
            if (closeFuture != null) {
                return closeFuture;
            }
            closing = true;
            var result = new CompletableFuture<Void>();
            closeFuture = result;
            cancelAutosave();
            cache.synchronous().cleanUp();
            cacheExecutor
                    .whenIdle()
                    .thenCompose(_ -> awaitEvictionWork())
                    .thenCompose(_ -> savePending())
                    .thenCompose(_ -> saveDirtyEntries())
                    .thenCompose(_ -> awaitSaveLanes())
                    .thenCompose(_ -> {
                        cache.synchronous().invalidateAll();
                        cache.synchronous().cleanUp();
                        return cacheExecutor.whenIdle();
                    })
                    .whenComplete((_, error) -> {
                        invalidationSubscription.close();
                        if (error == null) {
                            entryGenerations.clear();
                            result.complete(null);
                        } else {
                            result.completeExceptionally(error);
                        }
                    });
            return result;
        }
    }

    @Override
    public void close() {
        if (Bukkit.getServer() != null && Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("DataCache.close() blocks; use closeAsync() on the server thread.");
        }
        try {
            closeAsync().toCompletableFuture().get(30, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            LOGGER.log(
                    Level.SEVERE,
                    "Cache close timed out after 30s; some dirty entries may not have been persisted: "
                            + dirtyEntries.size() + " dirty entries remain");
        } catch (ExecutionException error) {
            LOGGER.log(Level.SEVERE, "Cache close failed", error.getCause());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.SEVERE, "Cache close was interrupted; dirty entries may not have been persisted");
        }
    }

    private void cancelAutosave() {
        autosaveTask.cancel();
    }

    private AsyncLoadingCache<K, CacheEntry<V>> createCache(CacheSettings settings) {
        var builder = Caffeine.newBuilder()
                .maximumSize(settings.maximumSize())
                .executor(cacheExecutor)
                .removalListener((RemovalListener<K, CacheEntry<V>>)
                        (key, entry, cause) -> CaffeineDataCache.this.onRemoval(key, entry));

        if (settings.expireAfterAccessEnabled()) {
            builder.expireAfterAccess(settings.expireAfterAccess());
        }

        if (settings.expireAfterWriteEnabled()) {
            builder.expireAfterWrite(settings.expireAfterWrite());
        }

        if (settings.recordStats()) {
            builder.recordStats();
        }

        return builder.buildAsync(this::loadEntry);
    }

    private CompletableFuture<CacheEntry<V>> loadEntry(K key, Executor ignored) {
        return repository
                .find(key)
                .thenApply(optional -> optional.orElseGet(() -> defaultValue.apply(key)))
                .thenApply(value -> {
                    Objects.requireNonNull(value, "defaultValue must not return null");
                    return createEntry(value);
                })
                .toCompletableFuture()
                .exceptionally(throwable -> {
                    throw new CacheLoadException("Could not load cache entry: " + key, throwable);
                });
    }

    private SchedulerTask startAutosave(CacheSettings settings) {
        if (!settings.autosaveEnabled()) {
            return SchedulerTask.noop();
        }

        return scheduler.asyncTimer(this::runAutosave, settings.autosaveInterval(), settings.autosaveInterval());
    }

    private void runAutosave() {
        if (!autosaveInProgress.compareAndSet(false, true)) {
            return;
        }

        saveDirtyEntries().thenCompose(_ -> savePending()).whenComplete((_, error) -> {
            autosaveInProgress.set(false);
            if (error != null) {
                LOGGER.log(Level.SEVERE, "Could not auto-save dirty cache entries", error);
            }
        });
    }

    private void onRemoval(@Nullable K key, @Nullable CacheEntry<V> entry) {
        if (key == null || entry == null) {
            return;
        }

        final V value;
        final SaveOrder order;
        long generation = generationOf(entry);
        synchronized (entry) {
            if (!entry.dirty()) {
                entryGenerations.remove(entry);
                return;
            }
            dirtyEntries.remove(key, entry);
            value = entry.value();
            order = new SaveOrder(generation, entry.version());
        }
        entryGenerations.remove(entry);

        if (!settings.saveOnEvict()) {
            enqueuePendingSave(key, new PendingSave<>(value, order));
            return;
        }

        var work = new CompletableFuture<Void>();
        evictionWork.put(order, work);
        persist(key, value, order).whenComplete((_, error) -> {
            if (error != null) {
                LOGGER.log(
                        Level.SEVERE,
                        error,
                        () -> "Could not save evicted cache entry: " + key + ". Queuing for retry.");
                enqueuePendingSave(key, new PendingSave<>(value, order));
            }
            work.complete(null);
            evictionWork.remove(order, work);
        });
    }

    private void enqueuePendingSave(K key, CacheEntry<V> entry) {
        enqueuePendingSave(key, new PendingSave<>(entry.value(), new SaveOrder(generationOf(entry), entry.version())));
    }

    private void enqueuePendingSave(K key, PendingSave<V> candidate) {
        pendingSaves.compute(
                key,
                (_, current) ->
                        current == null || candidate.order().compareTo(current.order()) > 0 ? candidate : current);
    }

    private CompletionStage<Void> savePending() {
        if (pendingSaves.isEmpty()) {
            return CompletionStages.completedVoid();
        }

        var entries = Map.copyOf(pendingSaves);
        return runBounded(List.copyOf(entries.entrySet()), e -> savePendingEntry(e.getKey(), e.getValue()));
    }

    private CompletionStage<Void> savePendingEntry(K key, PendingSave<V> pending) {
        return persist(key, pending.value(), pending.order())
                .thenRun(() -> {
                    pendingSaves.remove(key, pending);
                    // A pending save belongs to an evicted entry. It must never clear a newer
                    // in-cache entry even when both entries happen to have the same local version.
                })
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

    private CacheEntry<V> getRequiredEntry(K key) {
        return Optional.ofNullable(cache.synchronous().getIfPresent(key))
                .orElseThrow(() -> new CacheException(
                        "Cache entry is not loaded: " + key + ". Use getOrLoad(key) or load(key) first."));
    }

    private CacheEntry<V> createEntry(V value) {
        var entry = new CacheEntry<>(value);
        entryGenerations.put(entry, nextGeneration.incrementAndGet());
        return entry;
    }

    private long generationOf(CacheEntry<V> entry) {
        return entryGenerations.computeIfAbsent(entry, _ -> nextGeneration.incrementAndGet());
    }

    private CompletionStage<Void> persist(K key, V value, SaveOrder order) {
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

    private void onInvalidation(CacheInvalidation<K> invalidation) {
        if (cacheId.equals(invalidation.sourceId()) || closing) {
            return;
        }
        K key = invalidation.key();
        CacheEntry<V> entry = cache.synchronous().getIfPresent(key);
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            if (entry.dirty()) {
                return;
            }
            cache.synchronous().invalidate(key);
        }
    }

    private <T> CompletionStage<Void> runBounded(List<T> items, Function<T, CompletionStage<Void>> operation) {
        if (items.isEmpty()) {
            return CompletionStages.completedVoid();
        }
        return new AsyncWorkCoordinator<>(items, maximumConcurrentSaves, operation).start();
    }

    private CompletionStage<Void> awaitSaveLanes() {
        return allOf(saveLanes.values().stream().map(SaveLane::tail));
    }

    private CompletionStage<Void> awaitEvictionWork() {
        return allOf(List.copyOf(evictionWork.values()).stream());
    }

    private void ensureOpen() {
        if (closing) {
            throw new IllegalStateException("DataCache is closing or already closed.");
        }
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
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(order, "order");
        }
    }

    private record SaveSnapshot<V>(V value, SaveOrder order) {
        SaveSnapshot {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(order, "order");
        }
    }

    private record SaveOrder(long generation, long version) implements Comparable<SaveOrder> {

        private static final SaveOrder NONE = new SaveOrder(Long.MIN_VALUE, Long.MIN_VALUE);

        @Override
        public int compareTo(SaveOrder other) {
            int generationComparison = Long.compare(generation, other.generation);
            return generationComparison != 0 ? generationComparison : Long.compare(version, other.version);
        }
    }

    private record SaveTicket(CompletableFuture<Void> result, long sequence) {}

    private static final class AsyncWorkCoordinator<T> {

        private final List<T> items;
        private final int workerCount;
        private final Function<T, CompletionStage<Void>> operation;
        private final AtomicLong nextIndex = new AtomicLong();
        private final java.util.concurrent.atomic.AtomicInteger remainingWorkers;
        private final AtomicReference<@Nullable Throwable> firstFailure = new AtomicReference<>();
        private final CompletableFuture<Void> result = new CompletableFuture<>();

        private AsyncWorkCoordinator(
                List<T> items, int maximumConcurrency, Function<T, CompletionStage<Void>> operation) {
            this.items = List.copyOf(items);
            this.workerCount = Math.min(maximumConcurrency, items.size());
            this.operation = Objects.requireNonNull(operation, "operation");
            this.remainingWorkers = new java.util.concurrent.atomic.AtomicInteger(workerCount);
        }

        CompletionStage<Void> start() {
            for (int worker = 0; worker < workerCount; worker++) {
                advance();
            }
            return result;
        }

        private void advance() {
            while (true) {
                long index = nextIndex.getAndIncrement();
                if (index >= items.size()) {
                    workerFinished();
                    return;
                }
                final CompletableFuture<Void> future;
                try {
                    future = Objects.requireNonNull(
                                    operation.apply(items.get(Math.toIntExact(index))), "bulk operation returned null")
                            .toCompletableFuture();
                } catch (Throwable failure) {
                    recordFailure(failure);
                    continue;
                }
                if (future.isDone()) {
                    try {
                        future.getNow(null);
                    } catch (java.util.concurrent.CompletionException failure) {
                        recordFailure(failure.getCause() == null ? failure : failure.getCause());
                    }
                    continue;
                }
                var _ = future.whenComplete((_, error) -> {
                    if (error != null) {
                        recordFailure(error);
                    }
                    advance();
                });
                return;
            }
        }

        private void recordFailure(Throwable failure) {
            Throwable previous = firstFailure.get();
            if (previous == null) {
                if (firstFailure.compareAndSet(null, failure)) {
                    return;
                }
                previous = firstFailure.get();
            }
            if (previous != null && !previous.equals(failure)) {
                previous.addSuppressed(failure);
            }
        }

        private void workerFinished() {
            if (remainingWorkers.decrementAndGet() != 0) {
                return;
            }
            Throwable failure = firstFailure.get();
            if (failure == null) {
                result.complete(null);
            } else {
                result.completeExceptionally(failure);
            }
        }
    }

    private static final class TrackedExecutor implements Executor {

        private final Executor delegate;
        private final Object lock = new Object();
        private int activeTasks;
        private CompletableFuture<Void> idle = CompletableFuture.completedFuture(null);

        private TrackedExecutor(Executor delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public void execute(Runnable command) {
            Objects.requireNonNull(command, "command");
            synchronized (lock) {
                if (activeTasks == 0) {
                    idle = new CompletableFuture<>();
                }
                activeTasks++;
            }
            try {
                delegate.execute(() -> {
                    try {
                        command.run();
                    } finally {
                        taskFinished();
                    }
                });
            } catch (RuntimeException schedulingFailure) {
                taskFinished();
                throw schedulingFailure;
            }
        }

        CompletionStage<Void> whenIdle() {
            synchronized (lock) {
                return idle;
            }
        }

        private void taskFinished() {
            CompletableFuture<Void> completed = null;
            synchronized (lock) {
                activeTasks--;
                if (activeTasks == 0) {
                    completed = idle;
                }
            }
            if (completed != null) {
                completed.complete(null);
            }
        }
    }
}
