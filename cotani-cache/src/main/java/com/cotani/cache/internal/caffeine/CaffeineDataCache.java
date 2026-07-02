package com.cotani.cache.internal.caffeine;

import com.cotani.cache.api.DataCache;
import com.cotani.cache.entry.CacheEntry;
import com.cotani.cache.exception.CacheException;
import com.cotani.cache.exception.CacheLoadException;
import com.cotani.cache.exception.CacheSaveException;
import com.cotani.cache.future.CacheFuture;
import com.cotani.cache.policy.CacheSettings;
import com.cotani.cache.repository.CacheRepository;
import com.cotani.cache.stats.CacheStatsView;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public final class CaffeineDataCache<K, V> implements DataCache<K, V> {

    private static final Logger LOGGER = Logger.getLogger(CaffeineDataCache.class.getName());

    private final AsyncLoadingCache<K, CacheEntry<V>> cache;
    private final CacheRepository<K, V> repository;
    private final Supplier<V> defaultValue;
    private final PaperTaskScheduler scheduler;
    private final CacheSettings settings;
    private final SchedulerTask autosaveTask;
    private final Queue<PendingSave<K, V>> pendingSaves = new ConcurrentLinkedQueue<>();

    public CaffeineDataCache(
            CacheRepository<K, V> repository,
            Supplier<V> defaultValue,
            PaperTaskScheduler scheduler,
            CacheSettings settings) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.cache = createCache(settings);
        this.autosaveTask = startAutosave(settings);
    }

    @Override
    public V get(K key) {
        return find(key)
                .orElseThrow(() -> new CacheException(
                        "Cache entry is not loaded: " + key + ". Use getOrLoad(key) or load(key) first."));
    }

    @Override
    public Optional<V> find(K key) {
        var entry = cache.synchronous().getIfPresent(key);

        if (entry == null) {
            return Optional.empty();
        }

        return Optional.of(entry.value());
    }

    @Override
    public CacheFuture<V> getOrLoad(K key) {
        return CacheFuture.from(cache.get(key).thenApply(CacheEntry::value), scheduler);
    }

    @Override
    public CacheFuture<V> load(K key) {
        cache.synchronous().invalidate(key);
        return getOrLoad(key);
    }

    @Override
    public CacheFuture<V> update(K key, UnaryOperator<V> updater) {
        var entry = getRequiredEntry(key);
        var updated = entry.update(updater);
        return CacheFuture.completed(updated, scheduler);
    }

    @Override
    public CacheFuture<V> mutate(K key, Consumer<V> mutator) {
        var entry = getRequiredEntry(key);
        var mutated = entry.mutate(mutator);
        return CacheFuture.completed(mutated, scheduler);
    }

    @Override
    public void put(K key, V value) {
        cache.synchronous().put(key, new CacheEntry<>(value));
    }

    @Override
    public CacheFuture<Void> save(K key) {
        var entry = cache.synchronous().getIfPresent(key);

        if (entry == null) {
            return CacheFuture.completed(null, scheduler);
        }

        return repository
                .save(key, entry.value())
                .map(_ -> {
                    entry.markSaved();
                    return (Void) null;
                })
                .onFailure(error -> {
                    throw new CacheSaveException("Could not save cache entry: " + key, error);
                });
    }

    @Override
    public CacheFuture<Void> saveDirty() {
        var futures = cache.synchronous().asMap().entrySet().stream()
                .filter(entry -> entry.getValue().dirty())
                .map(entry -> save(entry.getKey()))
                .toList();

        return CacheFuture.allOf(futures, scheduler);
    }

    @Override
    public CacheFuture<Void> saveAll() {
        var futures =
                cache.synchronous().asMap().keySet().stream().map(this::save).toList();

        return CacheFuture.allOf(futures, scheduler);
    }

    @Override
    public void unload(K key) {
        cache.synchronous().invalidate(key);
    }

    @Override
    public void invalidate(K key) {
        cache.synchronous().invalidate(key);
    }

    @Override
    public boolean contains(K key) {
        return cache.synchronous().getIfPresent(key) != null;
    }

    @Override
    public void markDirty(K key) {
        var entry = getRequiredEntry(key);
        entry.markDirty();
    }

    @Override
    public int dirtyCount() {
        return (int) cache.synchronous().asMap().values().stream()
                .filter(CacheEntry::dirty)
                .count();
    }

    @Override
    public long size() {
        return cache.synchronous().estimatedSize();
    }

    @Override
    public Map<K, V> snapshot() {
        var values = cache.synchronous().asMap().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, entry -> entry.getValue().value()));

        return Map.copyOf(values);
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
                dirtyCount());
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        autosaveTask.cancel();
        return saveDirty().toCompletionStage().thenCompose(_ -> savePending()).thenRun(() -> {
            cache.synchronous().invalidateAll();
            cache.synchronous().cleanUp();
        });
    }

    @Override
    public void close() {
        autosaveTask.cancel();
        var _ = closeAsync().toCompletableFuture().whenComplete((_, error) -> {
            if (error != null) {
                LOGGER.log(Level.SEVERE, "Could not close cache gracefully", error);
            }
        });
    }

    private AsyncLoadingCache<K, CacheEntry<V>> createCache(CacheSettings settings) {
        var builder = Caffeine.newBuilder()
                .maximumSize(settings.maximumSize())
                .removalListener(
                        (@Nullable K key, @Nullable CacheEntry<V> entry, RemovalCause _) -> onRemoval(key, entry));

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

    private CompletableFuture<CacheEntry<V>> loadEntry(K key, Executor executor) {
        return repository
                .find(key)
                .map(optional -> optional.orElseGet(defaultValue))
                .map(CacheEntry::new)
                .toCompletableFuture()
                .exceptionally(throwable -> {
                    throw new CacheLoadException("Could not load cache entry: " + key, throwable);
                });
    }

    private SchedulerTask startAutosave(CacheSettings settings) {
        if (!settings.autosaveEnabled()) {
            return SchedulerTask.noop();
        }

        return scheduler.asyncTimer(
                () -> saveDirty()
                        .onFailure(error -> LOGGER.log(Level.SEVERE, "Could not auto-save dirty cache entries", error)),
                settings.autosaveInterval(),
                settings.autosaveInterval());
    }

    private void onRemoval(@Nullable K key, @Nullable CacheEntry<V> entry) {
        if (key == null || entry == null) {
            return;
        }

        if (!settings.saveOnEvict()) {
            return;
        }

        if (!entry.dirty()) {
            return;
        }

        repository.save(key, entry.value()).onFailure(error -> {
            LOGGER.log(Level.SEVERE, "Could not save evicted cache entry: " + key + ". Queuing for retry.", error);
            pendingSaves.offer(new PendingSave<>(key, entry.value()));
        });
    }

    private CompletionStage<Void> savePending() {
        var futures = pendingSaves.stream()
                .map(pending -> repository
                        .save(pending.key(), pending.value())
                        .toCompletionStage()
                        .whenComplete((_, error) -> {
                            if (error == null) {
                                pendingSaves.remove(pending);
                            }
                        }))
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures);
    }

    private CacheEntry<V> getRequiredEntry(K key) {
        var entry = cache.synchronous().getIfPresent(key);

        if (entry != null) {
            return entry;
        }

        throw new CacheException("Cache entry is not loaded: " + key + ". Use getOrLoad(key) or load(key) first.");
    }

    private record PendingSave<K, V>(K key, V value) {}
}
