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
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class TeleportEventNotifier {
    private final TeleportEventBus eventBus;
    private final Clock clock;

    public TeleportEventNotifier(TeleportEventBus eventBus, Clock clock) {
        this.eventBus = eventBus;
        this.clock = clock;
    }

    public CompletableFuture<CotaniPreTeleportEvent> firePreTeleport(
            Player player, Location from, Location resolvedTarget, TeleportCause cause, String source) {
        CotaniPreTeleportEvent event = new CotaniPreTeleportEvent(player, from, resolvedTarget, cause, source);
        return eventBus.callAsync(event).thenApply(_ -> event);
    }

    public CompletableFuture<Void> firePostTeleport(
            Player player, Location from, Location eventTarget, TeleportResult.Success result) {
        return eventBus.callAsync(new CotaniPostTeleportEvent(player, from, eventTarget, result));
    }

    public CompletableFuture<Void> fireFailure(TeleportResult.Failure failure) {
        return eventBus.callAsync(new CotaniTeleportFailEvent(failure.player(), failure));
    }

    public long elapsedMillis(Instant startedAt) {
        return Duration.between(startedAt, Instant.now(clock)).toMillis();
    }
}
