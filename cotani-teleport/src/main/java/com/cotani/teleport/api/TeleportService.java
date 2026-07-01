package com.cotani.teleport.api;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface TeleportService {
    CompletableFuture<TeleportResult> teleport(TeleportRequest request);

    default CompletableFuture<TeleportResult> teleport(Player player, Location target) {
        return teleport(player, target, TeleportOptions.defaults());
    }

    default CompletableFuture<TeleportResult> teleport(Player player, Location target, TeleportOptions options) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(options, "options");
        return teleport(TeleportRequest.builder()
                .player(player)
                .target(target)
                .cause(TeleportCause.PLUGIN_INTERNAL)
                .source("direct-call")
                .options(options)
                .build());
    }
}
