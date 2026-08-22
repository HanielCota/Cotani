package com.cotani.display.impl;

import com.cotani.api.InternalApi;
import com.cotani.display.api.HologramClickType;
import com.cotani.display.api.HologramService;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Paper/Folia listener that routes click interactions on Display {@link Interaction} entities to registered Hologram callbacks.
 */
@InternalApi
public final class DisplayInteractionListener implements Listener {

    private static final long DEBOUNCE_NANOS = Duration.ofMillis(100).toNanos();

    private final HologramService hologramService;
    private final ConcurrentHashMap<UUID, AtomicLong> lastClickNanos = new ConcurrentHashMap<>();

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

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastClickNanos.remove(event.getPlayer().getUniqueId());
    }

    private boolean isDebounced(UUID playerId) {
        var lastClick = lastClickNanos.computeIfAbsent(playerId, _ -> new AtomicLong(Long.MIN_VALUE));
        long now = System.nanoTime();
        while (true) {
            long previous = lastClick.get();
            if (previous != Long.MIN_VALUE && now - previous < DEBOUNCE_NANOS) {
                return true;
            }
            if (lastClick.compareAndSet(previous, now)) {
                return false;
            }
        }
    }

    public void clear() {
        lastClickNanos.clear();
    }
}
