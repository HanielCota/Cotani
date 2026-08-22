package com.cotani.cache.listener;

import com.cotani.cache.api.PlayerDataCache;
import com.cotani.cache.policy.CacheSettings;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Bukkit listener that loads player data on join and saves/unloads on quit.
 *
 * <p>Behavior is controlled by {@link CacheSettings} lifecycle flags.
 *
 * <p>Each join bumps a per-player generation so a late quit-save cannot unload a newer session
 * after a fast reconnect.
 *
 * @param <V> the player data type
 */
public final class PlayerDataCacheListener<V> implements Listener, AutoCloseable {
    private final PlayerDataCache<V> cache;
    private final CacheSettings settings;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, AtomicLong> generations = new ConcurrentHashMap<>();

    private PlayerDataCacheListener(PlayerDataCache<V> cache, CacheSettings settings, Logger logger) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public static <V> PlayerDataCacheListener<V> create(
            PlayerDataCache<V> cache, CacheSettings settings, Logger logger) {
        return new PlayerDataCacheListener<>(cache, settings, logger);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var playerId = event.getPlayer().getUniqueId();
        bumpGeneration(playerId);

        if (!settings.loadOnJoin()) {
            return;
        }

        cache.getOrLoadAsync(playerId).whenComplete((_, error) -> {
            if (error != null) {
                logger.log(Level.SEVERE, error, () -> "Could not load player cache entry for " + playerId);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var playerId = event.getPlayer().getUniqueId();
        long generation = currentGeneration(playerId);

        if (!settings.saveOnQuit()) {
            unloadIfNeeded(playerId, generation);
            return;
        }

        try {
            cache.saveAsync(playerId).whenComplete((_, error) -> {
                if (error != null) {
                    logger.log(Level.SEVERE, error, () -> "Could not save player cache entry for " + playerId);
                }
                unloadIfNeeded(playerId, generation);
            });
        } catch (RuntimeException closed) {
            logger.log(Level.FINE, closed, () -> "Skipping player cache save after close for " + playerId);
        }
    }

    private void unloadIfNeeded(UUID playerId, long generation) {
        if (currentGeneration(playerId) != generation) {
            return;
        }
        if (settings.unloadOnQuit()) {
            try {
                cache.unload(playerId);
            } catch (RuntimeException closed) {
                logger.log(Level.FINE, closed, () -> "Skipping player cache unload after close for " + playerId);
            }
        }
        generations.remove(playerId);
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        generations.clear();
    }

    private void bumpGeneration(UUID playerId) {
        generations.computeIfAbsent(playerId, _ -> new AtomicLong()).incrementAndGet();
    }

    private long currentGeneration(UUID playerId) {
        AtomicLong generation = generations.get(playerId);
        return generation == null ? 0L : generation.get();
    }
}
