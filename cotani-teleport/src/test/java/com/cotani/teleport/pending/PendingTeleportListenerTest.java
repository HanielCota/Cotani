package com.cotani.teleport.pending;

import static org.mockito.Mockito.*;

import com.cotani.teleport.api.PendingTeleportState;
import com.cotani.teleport.api.PendingTeleportView;
import com.cotani.teleport.api.TeleportCancelReason;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;

class PendingTeleportListenerTest {

    private final DefaultPendingTeleportService pendingService = mock(DefaultPendingTeleportService.class);
    private final PendingTeleportListener listener = new PendingTeleportListener(pendingService);

    @Test
    @SuppressWarnings("deprecation")
    void cancelsWhenPlayerAttacksDirectly() {
        UUID playerId = UUID.randomUUID();
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(playerId);

        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(attacker);

        listener.onDamageDealt(event);

        verify(pendingService).cancel(playerId, TeleportCancelReason.COMBAT);
    }

    @Test
    @SuppressWarnings("deprecation")
    void cancelsWhenPlayerAttacksWithProjectile() {
        UUID playerId = UUID.randomUUID();
        Player shooter = mock(Player.class);
        when(shooter.getUniqueId()).thenReturn(playerId);

        Projectile projectile = mock(Projectile.class);
        when(projectile.getShooter()).thenReturn(shooter);

        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(projectile);

        listener.onDamageDealt(event);

        verify(pendingService).cancel(playerId, TeleportCancelReason.COMBAT);
    }

    @Test
    void cancelsWhenPlayerTeleportsWhileWaiting() {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);

        PlayerTeleportEvent event = mock(PlayerTeleportEvent.class);
        when(event.getPlayer()).thenReturn(player);

        PendingTeleportView view = mock(PendingTeleportView.class);
        when(view.state()).thenReturn(PendingTeleportState.WAITING);
        when(pendingService.find(playerId)).thenReturn(Optional.of(view));

        listener.onTeleport(event);

        verify(pendingService).cancel(playerId, TeleportCancelReason.MOVED);
    }

    @Test
    void doesNotCancelWhenPlayerTeleportsWhileExecuting() {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);

        PlayerTeleportEvent event = mock(PlayerTeleportEvent.class);
        when(event.getPlayer()).thenReturn(player);

        PendingTeleportView view = mock(PendingTeleportView.class);
        when(view.state()).thenReturn(PendingTeleportState.EXECUTING);
        when(pendingService.find(playerId)).thenReturn(Optional.of(view));

        listener.onTeleport(event);

        verify(pendingService, never()).cancel(any(), any());
    }
}
