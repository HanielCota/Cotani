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

    private @Nullable CacheRepository<UUID, V> repository;
    private @Nullable PlayerValueFactory<V> defaultValue;
    private CacheSettings settings = CacheSettings.playerData();

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
        this.settings = Objects.requireNonNull(preset, "preset").settings();
        return this;
    }

    public PlayerDataCacheBuilder<V> settings(CacheSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        return this;
    }

    public PlayerDataCacheBuilder<V> maximumSize(long maximumSize) {
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

    public PlayerDataCacheBuilder<V> expireAfterAccess(Duration duration) {
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

    public PlayerDataCacheBuilder<V> expireAfterWrite(Duration duration) {
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

    public PlayerDataCacheBuilder<V> autosaveEvery(Duration duration) {
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

    public PlayerDataCacheBuilder<V> loadOnJoin() {
        this.settings = withLifecycle(true, settings.saveOnQuit(), settings.unloadOnQuit());
        return this;
    }

    public PlayerDataCacheBuilder<V> saveOnQuit() {
        this.settings = withLifecycle(settings.loadOnJoin(), true, settings.unloadOnQuit());
        return this;
    }

    public PlayerDataCacheBuilder<V> unloadOnQuit() {
        this.settings = withLifecycle(settings.loadOnJoin(), settings.saveOnQuit(), true);
        return this;
    }

    public PlayerDataCacheBuilder<V> saveOnEvict() {
        this.settings = new CacheSettings(
                settings.maximumSize(),
                settings.expireAfterAccess(),
                settings.expireAfterWrite(),
                settings.autosaveInterval(),
                settings.loadOnJoin(),
                settings.saveOnQuit(),
                settings.unloadOnQuit(),
                true,
                settings.recordStats());
        return this;
    }

    public PlayerDataCacheBuilder<V> recordStats() {
        this.settings = new CacheSettings(
                settings.maximumSize(),
                settings.expireAfterAccess(),
                settings.expireAfterWrite(),
                settings.autosaveInterval(),
                settings.loadOnJoin(),
                settings.saveOnQuit(),
                settings.unloadOnQuit(),
                settings.saveOnEvict(),
                true);
        return this;
    }

    public PlayerDataCache<V> build(Plugin plugin, PaperTaskScheduler scheduler) {
        validate();

        var resolvedRepository = resolveRepository(scheduler);
        var resolvedDefaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        var dataCache = createDataCache(resolvedRepository, scheduler);
        var playerCache = new CaffeinePlayerDataCache<>(dataCache, resolvedRepository, resolvedDefaultValue, scheduler);

        registerListener(plugin, playerCache);
        loadOnlinePlayers(plugin, playerCache);

        return playerCache;
    }

    private DataCache<UUID, V> createDataCache(CacheRepository<UUID, V> repository, PaperTaskScheduler scheduler) {
        return new CaffeineDataCache<>(
                repository,
                () -> {
                    throw new CacheException(
                            "Player default value requires Player context. Use getOrLoad(player) instead.");
                },
                scheduler,
                settings);
    }

    private void registerListener(Plugin plugin, PlayerDataCache<V> playerCache) {
        plugin.getServer()
                .getPluginManager()
                .registerEvents(new PlayerDataCacheListener<>(playerCache, settings, plugin.getLogger()), plugin);
    }

    private void loadOnlinePlayers(Plugin plugin, PlayerDataCache<V> playerCache) {
        if (!settings.loadOnJoin()) {
            return;
        }

        plugin.getServer().getOnlinePlayers().forEach(player -> playerCache.loadAsync(player.getUniqueId()));
    }

    private CacheRepository<UUID, V> resolveRepository(PaperTaskScheduler scheduler) {
        if (repository != null) {
            return repository;
        }

        return new NoopCacheRepository<>(scheduler);
    }

    private void validate() {
        if (defaultValue != null) {
            return;
        }

        throw new CacheException("Player default value factory is required for " + valueType.getName());
    }

    private CacheSettings withLifecycle(boolean loadOnJoin, boolean saveOnQuit, boolean unloadOnQuit) {
        return new CacheSettings(
                settings.maximumSize(),
                settings.expireAfterAccess(),
                settings.expireAfterWrite(),
                settings.autosaveInterval(),
                loadOnJoin,
                saveOnQuit,
                unloadOnQuit,
                settings.saveOnEvict(),
                settings.recordStats());
    }
}
