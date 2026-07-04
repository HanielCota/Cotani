package com.cotani.cache.internal.caffeine;

import com.cotani.cache.api.DataCache;
import com.cotani.cache.entry.CacheEntry;
import com.cotani.cache.exception.CacheException;
import com.cotani.cache.exception.CacheLoadException;
import com.cotani.cache.exception.CacheSaveException;
import com.cotani.cache.policy.CacheSettings;
import com.cotani.cache.repository.CacheRepository;
import com.cotani.cache.stats.CacheStatsView;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import com.cotani.task.util.CompletionStages;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
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
public final class CaffeineDataCache<K, V> implements DataCache<K, V> {

    private static final Logger LOGGER = Logger.getLogger(CaffeineDataCache.class.getName());

    private final AsyncLoadingCache<K, CacheEntry<V>> cache;
    private final CacheRepository<K, V> repository;
    private final Function<K, V> defaultValue;
    private final PaperTaskScheduler scheduler;
    private final CacheSettings settings;
    private final SchedulerTask autosaveTask;
    private final ConcurrentHashMap<K, V> pendingSaves = new ConcurrentHashMap<>();
    private final AtomicInteger dirtyCount = new AtomicInteger(0);
    private final AtomicBoolean autosaveInProgress = new AtomicBoolean(false);

    public CaffeineDataCache(
            CacheRepository<K, V> repository,
            Function<K, V> defaultValue,
            PaperTaskScheduler scheduler,
            CacheSettings settings) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.cache = createCache(settings);
        this.autosaveTask = startAutosave(settings);
    }

    private static CompletionStage<Void> allOf(java.util.stream.Stream<? extends CompletionStage<Void>> stages) {
        var array = stages.map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new);
        return array.length == 0 ? CompletionStages.completedVoid() : CompletableFuture.allOf(array);
    }

    @Override
    public V get(K key) {
        return find(key)
                .orElseThrow(() -> new CacheException(
                        "Cache entry is not loaded: " + key + ". Use getOrLoad(key) or load(key) first."));
    }

    @Override
    public Optional<V> find(K key) {
        return Optional.ofNullable(cache.synchronous().getIfPresent(key)).map(CacheEntry::value);
    }

    @Override
    public CompletionStage<V> getOrLoad(K key) {
        return cache.get(key).thenApply(CacheEntry::value);
    }

    @Override
    public CompletionStage<V> load(K key) {
        cache.synchronous().invalidate(key);
        return getOrLoad(key);
    }

    @Override
    public CompletionStage<V> update(K key, UnaryOperator<V> updater) {
        var entry = getRequiredEntry(key);
        var updated = entry.update(updater);
        dirtyCount.incrementAndGet();
        return CompletableFuture.completedFuture(updated);
    }

    @Override
    public CompletionStage<V> mutate(K key, Consumer<V> mutator) {
        var entry = getRequiredEntry(key);
        var mutated = entry.mutate(mutator);
        dirtyCount.incrementAndGet();
        return CompletableFuture.completedFuture(mutated);
    }

    @Override
    public void put(K key, V value) {
        cache.synchronous().put(key, new CacheEntry<>(value));
    }

    @Override
    public CompletionStage<Void> save(K key) {
        return Optional.ofNullable(cache.synchronous().getIfPresent(key))
                .map(entry -> Objects.requireNonNull(
                                repository.save(key, entry.value()), "repository.save returned null")
                        .thenRun(entry::markSaved)
                        .thenRun(dirtyCount::decrementAndGet)
                        .exceptionallyCompose(error -> {
                            throw new CacheSaveException("Could not save cache entry: " + key, error);
                        }))
                .orElseGet(CompletionStages::completedVoid);
    }

    @Override
    public CompletionStage<Void> saveDirty() {
        var dirtyKeys = cache.synchronous().asMap().entrySet().stream()
                .filter(e -> e.getValue().dirty())
                .map(Map.Entry::getKey)
                .toList();

        return allOf(dirtyKeys.stream().map(this::save));
    }

    @Override
    public CompletionStage<Void> saveAll() {
        return allOf(cache.synchronous().asMap().keySet().stream().map(this::save));
    }

    @Override
    public void unload(K key) {
        cache.synchronous().invalidate(key);
    }

    @Override
    public boolean contains(K key) {
        return cache.synchronous().getIfPresent(key) != null;
    }

    @Override
    public void markDirty(K key) {
        getRequiredEntry(key).markDirty();
        dirtyCount.incrementAndGet();
    }

    @Override
    public int dirtyCount() {
        return dirtyCount.get();
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
                dirtyCount.get());
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        cancelAutosave();
        return saveDirty()
                .thenCompose(_ -> savePending())
                .thenRun(cache.synchronous()::invalidateAll)
                .thenRun(cache.synchronous()::cleanUp);
    }

    @Override
    public void close() {
        cancelAutosave();
        closeAsync().toCompletableFuture().join();
    }

    private void cancelAutosave() {
        autosaveTask.cancel();
    }

    private AsyncLoadingCache<K, CacheEntry<V>> createCache(CacheSettings settings) {
        var builder = Caffeine.newBuilder()
                .maximumSize(settings.maximumSize())
                .removalListener(new RemovalListener<K, CacheEntry<V>>() {
                    @Override
                    public void onRemoval(@Nullable K key, @Nullable CacheEntry<V> entry, RemovalCause cause) {
                        CaffeineDataCache.this.onRemoval(key, entry);
                    }
                });

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
                    return new CacheEntry<>(value);
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

        saveDirty().whenComplete((_, error) -> {
            autosaveInProgress.set(false);
            if (error != null) {
                LOGGER.log(Level.SEVERE, "Could not auto-save dirty cache entries", error);
            }
        });
    }

    private void onRemoval(@Nullable K key, @Nullable CacheEntry<V> entry) {
        if (key == null || entry == null || !settings.saveOnEvict() || !entry.dirty()) {
            return;
        }

        Objects.requireNonNull(repository.save(key, entry.value()), "repository.save returned null")
                .whenComplete((_, error) -> {
                    if (error != null) {
                        LOGGER.log(
                                Level.SEVERE,
                                error,
                                () -> "Could not save evicted cache entry: " + key + ". Queuing for retry.");
                        pendingSaves.put(key, entry.value());
                    }
                });
    }

    private CompletionStage<Void> savePending() {
        if (pendingSaves.isEmpty()) {
            return CompletionStages.completedVoid();
        }

        var entries = Map.copyOf(pendingSaves);
        pendingSaves.clear();

        return allOf(entries.entrySet().stream()
                .map(e -> Objects.requireNonNull(
                                repository.save(e.getKey(), e.getValue()), "repository.save returned null")
                        .toCompletableFuture()));
    }

    private CacheEntry<V> getRequiredEntry(K key) {
        return Optional.ofNullable(cache.synchronous().getIfPresent(key))
                .orElseThrow(() -> new CacheException(
                        "Cache entry is not loaded: " + key + ". Use getOrLoad(key) or load(key) first."));
    }
}
