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
public final class PlayerDataCacheListener<V> implements Listener {

    private final PlayerDataCache<V> cache;
    private final CacheSettings settings;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, AtomicLong> generations = new ConcurrentHashMap<>();

    public PlayerDataCacheListener(PlayerDataCache<V> cache, CacheSettings settings, Logger logger) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var playerId = event.getPlayer().getUniqueId();
        bumpGeneration(playerId);

        if (!settings.loadOnJoin()) {
            return;
        }

        cache.loadAsync(playerId);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var playerId = event.getPlayer().getUniqueId();
        long generation = currentGeneration(playerId);

        if (!settings.saveOnQuit()) {
            unloadIfNeeded(playerId, generation);
            return;
        }

        cache.saveAsync(playerId).whenComplete((_, error) -> {
            if (error != null) {
                logger.log(Level.SEVERE, error, () -> "Could not save player cache entry for " + playerId);
            }
            unloadIfNeeded(playerId, generation);
        });
    }

    private void unloadIfNeeded(UUID playerId, long generation) {
        if (!settings.unloadOnQuit()) {
            return;
        }
        if (currentGeneration(playerId) != generation) {
            return;
        }
        cache.unload(playerId);
        generations.remove(playerId);
    }

    private void bumpGeneration(UUID playerId) {
        generations.computeIfAbsent(playerId, _ -> new AtomicLong()).incrementAndGet();
    }

    private long currentGeneration(UUID playerId) {
        AtomicLong generation = generations.get(playerId);
        return generation == null ? 0L : generation.get();
    }
}
