package com.cotani.cache.listener;

import com.cotani.cache.api.PlayerDataCache;
import com.cotani.cache.policy.CacheSettings;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerDataCacheListener<V> implements Listener {

    private final PlayerDataCache<V> cache;
    private final CacheSettings settings;
    private final Logger logger;

    public PlayerDataCacheListener(PlayerDataCache<V> cache, CacheSettings settings, Logger logger) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!settings.loadOnJoin()) {
            return;
        }

        var playerId = event.getPlayer().getUniqueId();
        cache.loadAsync(playerId);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var playerId = event.getPlayer().getUniqueId();

        if (!settings.saveOnQuit()) {
            unloadIfNeeded(playerId);
            return;
        }

        cache.saveAsync(playerId).thenRun(() -> unloadIfNeeded(playerId)).whenComplete((_, error) -> {
            if (error != null) {
                logger.log(Level.SEVERE, "Could not save player cache entry for " + playerId, error);
            }
        });
    }

    private void unloadIfNeeded(UUID playerId) {
        if (!settings.unloadOnQuit()) {
            return;
        }

        cache.unload(playerId);
    }
}
