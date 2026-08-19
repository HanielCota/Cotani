package com.cotani.display.impl;

import com.cotani.api.InternalApi;
import com.cotani.display.api.HologramClickType;
import com.cotani.display.api.HologramService;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Paper/Folia listener that routes click interactions on Display {@link Interaction} entities to registered Hologram callbacks.
 */
@InternalApi
public final class DisplayInteractionListener implements Listener {

    private static final long DEBOUNCE_NANOS = 100_000_000L; // 100ms

    private final HologramService hologramService;
    private final Map<UUID, Long> lastClickNanos = new ConcurrentHashMap<>();

    public DisplayInteractionListener(HologramService hologramService) {
        this.hologramService = Objects.requireNonNull(hologramService, "hologramService cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (!(event.getRightClicked() instanceof Interaction interaction)) {
            return;
        }

        var hologram = hologramService.findByEntityId(interaction.getUniqueId()).orElse(null);
        if (hologram == null) {
            return;
        }

        Player player = event.getPlayer();
        if (isDebounced(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        var handler = hologram.clickHandler().orElse(null);
        if (handler != null) {
            var clickType = player.isSneaking() ? HologramClickType.SHIFT_RIGHT_CLICK : HologramClickType.RIGHT_CLICK;
            handler.handleClick(player, hologram, clickType);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Interaction interaction)) {
            return;
        }

        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        var hologram = hologramService.findByEntityId(interaction.getUniqueId()).orElse(null);
        if (hologram == null) {
            return;
        }

        if (isDebounced(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        var handler = hologram.clickHandler().orElse(null);
        if (handler != null) {
            var clickType = player.isSneaking() ? HologramClickType.SHIFT_LEFT_CLICK : HologramClickType.LEFT_CLICK;
            handler.handleClick(player, hologram, clickType);
        }
    }

    private boolean isDebounced(UUID playerId) {
        long now = System.nanoTime();
        if (lastClickNanos.size() > 500) {
            lastClickNanos.entrySet().removeIf(entry -> (now - entry.getValue()) > 5_000_000_000L);
        }
        Long previous = lastClickNanos.put(playerId, now);
        return previous != null && (now - previous) < DEBOUNCE_NANOS;
    }

    public void clear() {
        lastClickNanos.clear();
    }
}
