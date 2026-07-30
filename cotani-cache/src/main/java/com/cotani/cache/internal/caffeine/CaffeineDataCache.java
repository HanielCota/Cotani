package com.cotani.cache.internal.caffeine;

import com.cotani.api.InternalApi;
import com.cotani.cache.api.DataCache;
import com.cotani.cache.entry.CacheEntry;
import com.cotani.cache.exception.CacheException;
import com.cotani.cache.exception.CacheSaveException;
import com.cotani.cache.invalidation.CacheInvalidation;
import com.cotani.cache.invalidation.CacheInvalidationBus;
import com.cotani.cache.invalidation.CacheInvalidationSubscription;
import com.cotani.cache.invalidation.NoopCacheInvalidationBus;
import com.cotani.cache.policy.CacheSettings;
import com.cotani.cache.repository.CacheRepository;
import com.cotani.cache.stats.CacheStatsView;
import com.cotani.task.api.AsyncTaskExecutor;
import com.cotani.task.api.DelayedTaskScheduler;
import com.cotani.task.api.PaperTaskScheduler;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
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
@InternalApi
public final class CaffeineDataCache<K, V> implements DataCache<K, V> {

    private static final Logger LOGGER = Logger.getLogger(CaffeineDataCache.class.getName());
    private static final String VALUE_PARAM = "value";

    private final AsyncLoadingCache<K, CacheEntry<V>> cache;
    private final CacheSettings settings;
    private final UUID cacheId = UUID.randomUUID();
    private final CacheInvalidationSubscription invalidationSubscription;
    private final TrackedExecutor cacheExecutor;
    private final DirtyEntryTracker<K, V> entryTracker;
    private final CacheEntryLoader<K, V> entryLoader;
    private final CacheSaveCoordinator<K, V> saveCoordinator;
    private final CacheAutosaveCoordinator autosaveCoordinator;
    private final Object closeLock = new Object();
    private volatile boolean closing;
    private @Nullable CompletableFuture<Void> closeFuture;

    private CaffeineDataCache(
            CacheRepository<K, V> repository,
            Function<K, V> defaultValue,
            PaperTaskScheduler scheduler,
            CacheSettings settings) {
        this(repository, defaultValue, scheduler, settings, 16, new NoopCacheInvalidationBus<>());
    }

    public static <K, V> CaffeineDataCache<K, V> create(
            CacheRepository<K, V> repository,
            Function<K, V> defaultValue,
            PaperTaskScheduler scheduler,
            CacheSettings settings) {
        return new CaffeineDataCache<>(repository, defaultValue, scheduler, settings);
    }

    private CaffeineDataCache(
            CacheRepository<K, V> repository,
            Function<K, V> defaultValue,
            PaperTaskScheduler scheduler,
            CacheSettings settings,
            int maximumConcurrentSaves,
            CacheInvalidationBus<K> invalidationBus) {
        this(repository, defaultValue, scheduler, scheduler, settings, maximumConcurrentSaves, invalidationBus);
    }

    public static <K, V> CaffeineDataCache<K, V> create(
            CacheRepository<K, V> repository,
            Function<K, V> defaultValue,
            PaperTaskScheduler scheduler,
            CacheSettings settings,
            int maximumConcurrentSaves,
            CacheInvalidationBus<K> invalidationBus) {
        return new CaffeineDataCache<>(
                repository, defaultValue, scheduler, settings, maximumConcurrentSaves, invalidationBus);
    }

    private CaffeineDataCache(
            CacheRepository<K, V> repository,
            Function<K, V> defaultValue,
            AsyncTaskExecutor asyncExecutor,
            DelayedTaskScheduler delayedTaskScheduler,
            CacheSettings settings,
            int maximumConcurrentSaves,
            CacheInvalidationBus<K> invalidationBus) {
        this.settings = Objects.requireNonNull(settings, "settings");
        var validatedExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor");
        var validatedDelays = Objects.requireNonNull(delayedTaskScheduler, "delayedTaskScheduler");
        var validatedInvalidationBus = Objects.requireNonNull(invalidationBus, "invalidationBus");
        this.cacheExecutor = new TrackedExecutor(validatedExecutor.asyncExecutor());
        this.entryTracker = new DirtyEntryTracker<>();
        this.entryLoader = new CacheEntryLoader<>(repository, defaultValue, entryTracker::createEntry);
        this.saveCoordinator =
                new CacheSaveCoordinator<>(repository, validatedInvalidationBus, cacheId, maximumConcurrentSaves);
        this.cache = createCache(settings);
        this.invalidationSubscription = validatedInvalidationBus.subscribe(this::onInvalidation);
        this.autosaveCoordinator = new CacheAutosaveCoordinator(
                validatedDelays, settings, () -> saveDirtyEntries().thenCompose(_ -> saveCoordinator.savePending()));
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
                entryTracker.markDirty(key, entry);
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
                entryTracker.markDirty(key, entry);
            }
        }
        return CompletableFuture.completedFuture(entry.value());
    }

    @Override
    public void put(K key, V value) {
        ensureOpen();
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, VALUE_PARAM);
        CacheEntry<V> previous = cache.synchronous().getIfPresent(key);
        if (previous != null) {
            synchronized (previous) {
                if (previous.dirty()) {
                    // Capture dirty value before replace; mark clean so onRemoval cannot save it twice.
                    saveCoordinator.queue(
                            key,
                            previous.value(),
                            new SaveOrder(entryTracker.generationOf(previous), previous.version()));
                    previous.markSavedIfVersionMatches(previous.version());
                    entryTracker.markClean(key, previous);
                }
            }
        }
        cache.synchronous().put(key, entryTracker.createEntry(value));
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
                        snapshot = new SaveSnapshot<>(
                                entry.value(), new SaveOrder(entryTracker.generationOf(entry), entry.version()));
                    }
                    return saveCoordinator
                            .persist(key, snapshot.value(), snapshot.order())
                            .thenRun(() -> {
                                synchronized (entry) {
                                    if (entry.markSavedIfVersionMatches(
                                            snapshot.order().version())) {
                                        entryTracker.markClean(key, entry);
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
        return saveCoordinator.runBounded(entryTracker.dirtyKeys(), this::saveEntry);
    }

    @Override
    public CompletionStage<Void> saveAll() {
        ensureOpen();
        return saveCoordinator.runBounded(
                List.copyOf(cache.synchronous().asMap().keySet()), this::saveEntry);
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
                entryTracker.markDirty(key, entry);
            }
        }
    }

    @Override
    public int dirtyCount() {
        return entryTracker.dirtyCount();
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
                entryTracker.dirtyCount());
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
            CompletionStage<Void> autosaveIdle = autosaveCoordinator.cancelAndAwait();
            cache.synchronous().cleanUp();
            autosaveIdle
                    .thenCompose(_ -> cacheExecutor.whenIdle())
                    .thenCompose(_ -> saveCoordinator.awaitEvictionWork())
                    .thenCompose(_ -> saveCoordinator.savePending())
                    .thenCompose(_ -> saveDirtyEntries())
                    .thenCompose(_ -> saveCoordinator.awaitSaveLanes())
                    .thenCompose(_ -> {
                        cache.synchronous().invalidateAll();
                        cache.synchronous().cleanUp();
                        return cacheExecutor.whenIdle();
                    })
                    .whenComplete((_, error) -> {
                        invalidationSubscription.close();
                        if (error == null) {
                            entryTracker.clearGenerations();
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
                            + entryTracker.dirtyCount() + " dirty entries remain");
        } catch (ExecutionException error) {
            LOGGER.log(Level.SEVERE, "Cache close failed", error.getCause());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.SEVERE, "Cache close was interrupted; dirty entries may not have been persisted");
        }
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

        return builder.buildAsync((key, _) -> entryLoader.load(key));
    }

    @SuppressWarnings("java:S2445") // CacheEntry is internally owned and deliberately acts as the per-entry lock.
    private void onRemoval(@Nullable K key, @Nullable CacheEntry<V> entry) {
        if (key == null || entry == null) {
            return;
        }

        final V value;
        final SaveOrder order;
        long generation = entryTracker.generationOf(entry);
        synchronized (entry) {
            if (!entry.dirty()) {
                entryTracker.forget(entry);
                return;
            }
            entryTracker.markClean(key, entry);
            value = entry.value();
            order = new SaveOrder(generation, entry.version());
        }
        entryTracker.forget(entry);

        if (!settings.saveOnEvict()) {
            saveCoordinator.queue(key, value, order);
            return;
        }

        saveCoordinator.saveEvicted(key, value, order);
    }

    private CacheEntry<V> getRequiredEntry(K key) {
        return Optional.ofNullable(cache.synchronous().getIfPresent(key))
                .orElseThrow(() -> new CacheException(
                        "Cache entry is not loaded: " + key + ". Use getOrLoad(key) or load(key) first."));
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

    private void ensureOpen() {
        if (closing) {
            throw new IllegalStateException("DataCache is closing or already closed.");
        }
    }

    private record SaveSnapshot<V>(V value, SaveOrder order) {
        SaveSnapshot {
            Objects.requireNonNull(value, VALUE_PARAM);
            Objects.requireNonNull(order, "order");
        }
    }
}
