package com.cotani.teleport.internal;

import com.cotani.api.InternalApi;
import com.cotani.teleport.api.PlayerResolver;
import com.cotani.teleport.api.TeleportCause;
import com.cotani.teleport.api.TeleportResult;
import com.cotani.teleport.event.CotaniPostTeleportEvent;
import com.cotani.teleport.event.CotaniPreTeleportEvent;
import com.cotani.teleport.event.CotaniTeleportFailEvent;
import com.cotani.teleport.event.TeleportEventBus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

@InternalApi
public final class TeleportEventNotifier {
    private final TeleportEventBus eventBus;
    private final Clock clock;
    private final PlayerResolver playerResolver;

    public TeleportEventNotifier(TeleportEventBus eventBus, Clock clock) {
        this(eventBus, clock, PlayerResolver.bukkit());
    }

    public TeleportEventNotifier(TeleportEventBus eventBus, Clock clock, PlayerResolver playerResolver) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.playerResolver = Objects.requireNonNull(playerResolver, "playerResolver");
    }

    public CompletionStage<@Nullable CotaniPreTeleportEvent> firePreTeleport(
            UUID playerId, Location from, Location resolvedTarget, TeleportCause cause, String source) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(resolvedTarget, "resolvedTarget");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(source, "source");

        return eventBus.dispatchIfPresentOnEntityAsync(playerId, () -> {
                    Player player = playerResolver.resolve(playerId);
                    return player == null
                            ? null
                            : new CotaniPreTeleportEvent(player, from, resolvedTarget, cause, source);
                })
                .thenApply(optionalEvent -> optionalEvent.orElse(null));
    }

    public CotaniPreTeleportEvent firePreTeleportSync(
            Player player, Location from, Location resolvedTarget, TeleportCause cause, String source) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(resolvedTarget, "resolvedTarget");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(source, "source");

        CotaniPreTeleportEvent event = new CotaniPreTeleportEvent(player, from, resolvedTarget, cause, source);

        return eventBus.callPreTeleportSync(event);
    }

    public CompletionStage<Void> firePostTeleport(
            UUID playerId, Location from, Location eventTarget, TeleportResult.Success result) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(eventTarget, "eventTarget");
        Objects.requireNonNull(result, "result");

        return eventBus.dispatchIfPresentOnEntityAsync(playerId, () -> {
                    Player player = playerResolver.resolve(playerId);
                    return player == null ? null : new CotaniPostTeleportEvent(player, from, eventTarget, result);
                })
                .thenApply(_ -> null);
    }

    public CompletionStage<Void> fireFailure(TeleportResult.Failure failure) {
        Objects.requireNonNull(failure, "failure");

        return eventBus.dispatchIfPresentOnEntityAsync(failure.playerId(), () -> {
                    Player player = playerResolver.resolve(failure.playerId());
                    return player == null ? null : new CotaniTeleportFailEvent(player, failure);
                })
                .thenApply(_ -> null);
    }

    public long elapsedMillis(Instant startedAt) {
        return Duration.between(startedAt, Instant.now(clock)).toMillis();
    }
}
