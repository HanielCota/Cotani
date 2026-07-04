package com.cotani.cache.builder;

import com.cotani.cache.api.DataCache;
import com.cotani.cache.exception.CacheException;
import com.cotani.cache.internal.caffeine.CaffeineDataCache;
import com.cotani.cache.policy.CachePreset;
import com.cotani.cache.policy.CacheSettings;
import com.cotani.cache.policy.CacheSettingsBuilder;
import com.cotani.cache.repository.CacheRepository;
import com.cotani.cache.repository.NoopCacheRepository;
import com.cotani.task.api.PaperTaskScheduler;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public final class DataCacheBuilder<K, V> {

    private final Class<K> keyType;
    private final Class<V> valueType;
    private final CacheSettingsBuilder settingsBuilder = CacheSettings.builder();
    private @Nullable CacheRepository<K, V> repository;
    private @Nullable Function<K, V> defaultValue;

    public DataCacheBuilder(Class<K> keyType, Class<V> valueType) {
        this.keyType = Objects.requireNonNull(keyType, "keyType");
        this.valueType = Objects.requireNonNull(valueType, "valueType");
    }

    public DataCacheBuilder<K, V> repository(CacheRepository<K, V> repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
        return this;
    }

    public DataCacheBuilder<K, V> defaultValue(Supplier<V> defaultValue) {
        this.defaultValue =
                _ -> Objects.requireNonNull(defaultValue, "defaultValue").get();
        return this;
    }

    public DataCacheBuilder<K, V> defaultValue(Function<K, V> defaultValue) {
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        return this;
    }

    public DataCacheBuilder<K, V> preset(CachePreset preset) {
        Objects.requireNonNull(preset, "preset");
        this.settingsBuilder.maximumSize(preset.settings().maximumSize());
        this.settingsBuilder.expireAfterAccess(preset.settings().expireAfterAccess());
        this.settingsBuilder.expireAfterWrite(preset.settings().expireAfterWrite());
        this.settingsBuilder.autosaveInterval(preset.settings().autosaveInterval());
        this.settingsBuilder.loadOnJoin(preset.settings().loadOnJoin());
        this.settingsBuilder.saveOnQuit(preset.settings().saveOnQuit());
        this.settingsBuilder.unloadOnQuit(preset.settings().unloadOnQuit());
        this.settingsBuilder.saveOnEvict(preset.settings().saveOnEvict());
        this.settingsBuilder.recordStats(preset.settings().recordStats());
        return this;
    }

    public DataCacheBuilder<K, V> settings(CacheSettings settings) {
        Objects.requireNonNull(settings, "settings");
        this.settingsBuilder.maximumSize(settings.maximumSize());
        this.settingsBuilder.expireAfterAccess(settings.expireAfterAccess());
        this.settingsBuilder.expireAfterWrite(settings.expireAfterWrite());
        this.settingsBuilder.autosaveInterval(settings.autosaveInterval());
        this.settingsBuilder.loadOnJoin(settings.loadOnJoin());
        this.settingsBuilder.saveOnQuit(settings.saveOnQuit());
        this.settingsBuilder.unloadOnQuit(settings.unloadOnQuit());
        this.settingsBuilder.saveOnEvict(settings.saveOnEvict());
        this.settingsBuilder.recordStats(settings.recordStats());
        return this;
    }

    public DataCacheBuilder<K, V> maximumSize(long maximumSize) {
        this.settingsBuilder.maximumSize(maximumSize);
        return this;
    }

    public DataCacheBuilder<K, V> expireAfterAccess(Duration duration) {
        this.settingsBuilder.expireAfterAccess(duration);
        return this;
    }

    public DataCacheBuilder<K, V> expireAfterWrite(Duration duration) {
        this.settingsBuilder.expireAfterWrite(duration);
        return this;
    }

    public DataCacheBuilder<K, V> autosaveEvery(Duration duration) {
        this.settingsBuilder.autosaveInterval(duration);
        return this;
    }

    public DataCache<K, V> build(PaperTaskScheduler scheduler) {
        validate();

        var resolvedRepository = resolveRepository();
        var resolvedDefaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        return new CaffeineDataCache<>(resolvedRepository, resolvedDefaultValue, scheduler, settingsBuilder.build());
    }

    private void validate() {
        if (defaultValue != null) {
            return;
        }

        throw new CacheException(
                "Default value supplier is required for cache " + keyType.getName() + " -> " + valueType.getName());
    }

    private CacheRepository<K, V> resolveRepository() {
        if (repository != null) {
            return repository;
        }

        return new NoopCacheRepository<>();
    }
}
