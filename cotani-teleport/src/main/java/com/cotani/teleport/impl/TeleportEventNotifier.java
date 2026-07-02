package com.cotani.teleport.impl;

import com.cotani.teleport.api.TeleportCause;
import com.cotani.teleport.api.TeleportResult;
import com.cotani.teleport.event.CotaniPostTeleportEvent;
import com.cotani.teleport.event.CotaniPreTeleportEvent;
import com.cotani.teleport.event.CotaniTeleportFailEvent;
import com.cotani.teleport.event.TeleportEventBus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class TeleportEventNotifier {
    private final TeleportEventBus eventBus;
    private final Clock clock;

    public TeleportEventNotifier(TeleportEventBus eventBus, Clock clock) {
        this.eventBus = eventBus;
        this.clock = clock;
    }

    public CompletionStage<CotaniPreTeleportEvent> firePreTeleport(
            UUID playerId, Location from, Location resolvedTarget, TeleportCause cause, String source) {
        Player player = resolvePlayer(playerId);
        CotaniPreTeleportEvent event = new CotaniPreTeleportEvent(player, from, resolvedTarget, cause, source);
        return eventBus.callAsync(event, player).thenApply(_ -> event);
    }

    public CompletionStage<Void> firePostTeleport(
            UUID playerId, Location from, Location eventTarget, TeleportResult.Success result) {
        Player player = resolvePlayer(playerId);
        return eventBus.callAsync(new CotaniPostTeleportEvent(player, from, eventTarget, result), player);
    }

    public CompletionStage<Void> fireFailure(TeleportResult.Failure failure) {
        Player player = resolvePlayer(failure.playerId());
        return eventBus.callAsync(new CotaniTeleportFailEvent(player, failure), player);
    }

    public long elapsedMillis(Instant startedAt) {
        return Duration.between(startedAt, Instant.now(clock)).toMillis();
    }

    private Player resolvePlayer(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            throw new IllegalStateException("Player is not online: " + playerId);
        }
        return player;
    }
}
