package com.cotani.teleport.pending;

import com.cotani.teleport.api.PendingTeleportState;
import com.cotani.teleport.api.TeleportCancelReason;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

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

    @EventHandler(ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            pendingService.cancel(player.getUniqueId(), TeleportCancelReason.COMBAT);
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            pendingService.cancel(player.getUniqueId(), TeleportCancelReason.COMBAT);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        pendingService.find(playerId).ifPresent(view -> {
            if (view.state() == PendingTeleportState.WAITING) {
                pendingService.cancel(playerId, TeleportCancelReason.MOVED);
            }
        });
    }
}
