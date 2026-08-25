package com.cotani.nametag.internal;

import com.cotani.api.InternalApi;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Paper/Folia listener that coordinates player lifecycle events with {@link DefaultNametagModule}.
 */
@InternalApi
public final class NametagPlayerListener implements Listener {

    private final DefaultNametagModule module;

    public NametagPlayerListener(DefaultNametagModule module) {
        this.module = Objects.requireNonNull(module, "Parameter 'module' must not be null");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        module.handlePlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        module.handlePlayerQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        module.handlePlayerRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        module.handlePlayerRefresh(event.getPlayer());
    }
}
