package com.cotani.cache.builder;

import com.cotani.cache.api.DataCache;
import com.cotani.cache.exception.CacheException;
import com.cotani.cache.internal.caffeine.CaffeineDataCache;
import com.cotani.cache.policy.CachePreset;
import com.cotani.cache.policy.CacheSettings;
import com.cotani.cache.repository.CacheRepository;
import com.cotani.cache.repository.NoopCacheRepository;
import com.cotani.task.api.PaperTaskScheduler;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public final class DataCacheBuilder<K, V> {

    private final Class<K> keyType;
    private final Class<V> valueType;

    private @Nullable CacheRepository<K, V> repository;
    private @Nullable Supplier<V> defaultValue;
    private CacheSettings settings = CacheSettings.temporary();

    public DataCacheBuilder(Class<K> keyType, Class<V> valueType) {
        this.keyType = Objects.requireNonNull(keyType, "keyType");
        this.valueType = Objects.requireNonNull(valueType, "valueType");
    }

    public DataCacheBuilder<K, V> repository(CacheRepository<K, V> repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
        return this;
    }

    public DataCacheBuilder<K, V> defaultValue(Supplier<V> defaultValue) {
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        return this;
    }

    public DataCacheBuilder<K, V> preset(CachePreset preset) {
        this.settings = Objects.requireNonNull(preset, "preset").settings();
        return this;
    }

    public DataCacheBuilder<K, V> settings(CacheSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        return this;
    }

    public DataCacheBuilder<K, V> maximumSize(long maximumSize) {
        this.settings = new CacheSettings(
                maximumSize,
                settings.expireAfterAccess(),
                settings.expireAfterWrite(),
                settings.autosaveInterval(),
                settings.loadOnJoin(),
                settings.saveOnQuit(),
                settings.unloadOnQuit(),
                settings.saveOnEvict(),
                settings.recordStats());
        return this;
    }

    public DataCacheBuilder<K, V> expireAfterAccess(Duration duration) {
        this.settings = new CacheSettings(
                settings.maximumSize(),
                duration,
                settings.expireAfterWrite(),
                settings.autosaveInterval(),
                settings.loadOnJoin(),
                settings.saveOnQuit(),
                settings.unloadOnQuit(),
                settings.saveOnEvict(),
                settings.recordStats());
        return this;
    }

    public DataCacheBuilder<K, V> expireAfterWrite(Duration duration) {
        this.settings = new CacheSettings(
                settings.maximumSize(),
                settings.expireAfterAccess(),
                duration,
                settings.autosaveInterval(),
                settings.loadOnJoin(),
                settings.saveOnQuit(),
                settings.unloadOnQuit(),
                settings.saveOnEvict(),
                settings.recordStats());
        return this;
    }

    public DataCacheBuilder<K, V> autosaveEvery(Duration duration) {
        this.settings = new CacheSettings(
                settings.maximumSize(),
                settings.expireAfterAccess(),
                settings.expireAfterWrite(),
                duration,
                settings.loadOnJoin(),
                settings.saveOnQuit(),
                settings.unloadOnQuit(),
                settings.saveOnEvict(),
                settings.recordStats());
        return this;
    }

    public DataCache<K, V> build(PaperTaskScheduler scheduler) {
        validate();

        var resolvedRepository = resolveRepository(scheduler);
        var resolvedDefaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        return new CaffeineDataCache<>(resolvedRepository, resolvedDefaultValue, scheduler, settings);
    }

    private void validate() {
        if (defaultValue != null) {
            return;
        }

        throw new CacheException(
                "Default value supplier is required for cache " + keyType.getName() + " -> " + valueType.getName());
    }

    private CacheRepository<K, V> resolveRepository(PaperTaskScheduler scheduler) {
        if (repository != null) {
            return repository;
        }

        return new NoopCacheRepository<>(scheduler);
    }
}
