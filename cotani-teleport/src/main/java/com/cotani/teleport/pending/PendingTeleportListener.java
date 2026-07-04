package com.cotani.teleport.pending;

import com.cotani.teleport.api.TeleportCancelReason;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PendingTeleportListener implements Listener {
    private final DefaultPendingTeleportService pendingService;

    public PendingTeleportListener(DefaultPendingTeleportService pendingService) {
        this.pendingService = Objects.requireNonNull(pendingService, "pendingService");
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!pendingService.hasPending(event.getPlayer().getUniqueId())) {
            return;
        }
        if (PendingTeleportCancellationPolicy.shouldCancel(event.getFrom(), event.getTo())) {
            pendingService.cancel(event.getPlayer().getUniqueId(), TeleportCancelReason.MOVED);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            pendingService.cancel(player.getUniqueId(), TeleportCancelReason.DAMAGED);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingService.cancel(event.getPlayer().getUniqueId(), TeleportCancelReason.QUIT);
    }
}
