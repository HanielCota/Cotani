package com.cotani.cache.builder;

import com.cotani.cache.api.DataCache;
import com.cotani.cache.api.PlayerDataCache;
import com.cotani.cache.api.PlayerValueFactory;
import com.cotani.cache.exception.CacheException;
import com.cotani.cache.internal.caffeine.CaffeineDataCache;
import com.cotani.cache.internal.caffeine.CaffeinePlayerDataCache;
import com.cotani.cache.listener.PlayerDataCacheListener;
import com.cotani.cache.policy.CachePreset;
import com.cotani.cache.policy.CacheSettings;
import com.cotani.cache.policy.CacheSettingsBuilder;
import com.cotani.cache.repository.CacheRepository;
import com.cotani.cache.repository.NoopCacheRepository;
import com.cotani.task.api.PaperTaskScheduler;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

public final class PlayerDataCacheBuilder<V> {

    private final Class<V> valueType;
    private final CacheSettingsBuilder settingsBuilder = CacheSettings.builder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofMinutes(30))
            .autosaveInterval(Duration.ofMinutes(5))
            .loadOnJoin(true)
            .saveOnQuit(true)
            .unloadOnQuit(true)
            .saveOnEvict(true)
            .recordStats(true);
    private @Nullable CacheRepository<UUID, V> repository;
    private @Nullable PlayerValueFactory<V> defaultValue;

    public PlayerDataCacheBuilder(Class<V> valueType) {
        this.valueType = Objects.requireNonNull(valueType, "valueType");
    }

    public PlayerDataCacheBuilder<V> repository(CacheRepository<UUID, V> repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
        return this;
    }

    public PlayerDataCacheBuilder<V> defaultValue(PlayerValueFactory<V> defaultValue) {
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        return this;
    }

    public PlayerDataCacheBuilder<V> preset(CachePreset preset) {
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

    public PlayerDataCacheBuilder<V> settings(CacheSettings settings) {
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

    public PlayerDataCacheBuilder<V> maximumSize(long maximumSize) {
        this.settingsBuilder.maximumSize(maximumSize);
        return this;
    }

    public PlayerDataCacheBuilder<V> expireAfterAccess(Duration duration) {
        this.settingsBuilder.expireAfterAccess(duration);
        return this;
    }

    public PlayerDataCacheBuilder<V> expireAfterWrite(Duration duration) {
        this.settingsBuilder.expireAfterWrite(duration);
        return this;
    }

    public PlayerDataCacheBuilder<V> autosaveEvery(Duration duration) {
        this.settingsBuilder.autosaveInterval(duration);
        return this;
    }

    public PlayerDataCacheBuilder<V> loadOnJoin() {
        this.settingsBuilder.loadOnJoin(true);
        return this;
    }

    public PlayerDataCacheBuilder<V> saveOnQuit() {
        this.settingsBuilder.saveOnQuit(true);
        return this;
    }

    public PlayerDataCacheBuilder<V> unloadOnQuit() {
        this.settingsBuilder.unloadOnQuit(true);
        return this;
    }

    public PlayerDataCacheBuilder<V> saveOnEvict() {
        this.settingsBuilder.saveOnEvict(true);
        return this;
    }

    public PlayerDataCacheBuilder<V> recordStats() {
        this.settingsBuilder.recordStats(true);
        return this;
    }

    public PlayerDataCache<V> build(Plugin plugin, PaperTaskScheduler scheduler) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(scheduler, "scheduler");
        validate();

        var resolvedRepository = resolveRepository();
        var resolvedDefaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        var dataCache = createDataCache(resolvedRepository, scheduler, resolvedDefaultValue);
        var playerCache = new CaffeinePlayerDataCache<>(dataCache, resolvedRepository, resolvedDefaultValue, scheduler);

        registerListener(plugin, playerCache);
        loadOnlinePlayers(plugin, playerCache);

        return playerCache;
    }

    private DataCache<UUID, V> createDataCache(
            CacheRepository<UUID, V> repository, PaperTaskScheduler scheduler, PlayerValueFactory<V> defaultValue) {
        return new CaffeineDataCache<>(repository, defaultValue::create, scheduler, settingsBuilder.build());
    }

    private void registerListener(Plugin plugin, PlayerDataCache<V> playerCache) {
        plugin.getServer()
                .getPluginManager()
                .registerEvents(
                        new PlayerDataCacheListener<>(playerCache, settingsBuilder.build(), plugin.getLogger()),
                        plugin);
    }

    private void loadOnlinePlayers(Plugin plugin, PlayerDataCache<V> playerCache) {
        if (!settingsBuilder.build().loadOnJoin()) {
            return;
        }

        plugin.getServer().getOnlinePlayers().forEach(player -> playerCache.loadAsync(player.getUniqueId()));
    }

    private CacheRepository<UUID, V> resolveRepository() {
        if (repository != null) {
            return repository;
        }

        return new NoopCacheRepository<>();
    }

    private void validate() {
        if (defaultValue != null) {
            return;
        }

        throw new CacheException("Player default value factory is required for " + valueType.getName());
    }
}
