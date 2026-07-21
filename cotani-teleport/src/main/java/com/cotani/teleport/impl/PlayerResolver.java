package com.cotani.teleport.impl;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a player UUID to a live {@link Player} instance.
 *
 * <p>This abstraction exists so the teleport pipeline can be tested without touching
 * {@link Bukkit#getPlayer(UUID)} statically.
 */
@FunctionalInterface
public interface PlayerResolver {

    @Nullable
    Player resolve(UUID playerId);

    static PlayerResolver bukkit() {
        return Bukkit::getPlayer;
    }
}
