package com.cotani.teleport.api;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface TeleportService {
    CompletionStage<TeleportResult> teleport(TeleportRequest request);

    /**
     * @deprecated Accepts a live {@link Player}; must be called on the main thread.
     * Prefer {@link #teleport(TeleportRequest)} or a {@code UUID}-based entry point.
     */
    @Deprecated
    default CompletionStage<TeleportResult> teleport(Player player, Location target) {
        return teleport(player, target, TeleportOptions.defaults());
    }

    /**
     * @deprecated Accepts a live {@link Player}; must be called on the main thread.
     * Prefer {@link #teleport(TeleportRequest)} or a {@code UUID}-based entry point.
     */
    @Deprecated
    default CompletionStage<TeleportResult> teleport(Player player, Location target, TeleportOptions options) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(options, "options");
        return teleport(TeleportRequest.builder()
                .playerId(player.getUniqueId())
                .target(target)
                .cause(TeleportCause.PLUGIN_INTERNAL)
                .source("direct-call")
                .options(options)
                .build());
    }
}
