package com.cotani.hud.internal;

import com.cotani.api.InternalApi;
import com.cotani.hud.api.HudModule;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Event listener that cleans up player HUD resources on disconnect.
 */
@InternalApi
public final class HudPlayerQuitListener implements Listener {

    private final HudModule hudModule;

    public HudPlayerQuitListener(HudModule hudModule) {
        this.hudModule = Objects.requireNonNull(hudModule, "Parameter 'hudModule' must not be null");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        hudModule.clear(event.getPlayer());
    }
}
