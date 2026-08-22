package com.cotani.nametag.api;

import java.util.Optional;
import org.bukkit.entity.Player;

/**
 * Functional SPI for dynamically resolving player nametags per viewer.
 *
 * <p>Registered providers are queried before falling back to player-specific or global nametags.
 *
 * <p><b>Threading contract:</b> Implementations are invoked on the viewer's entity/region thread and
 * must be non-blocking and memory-only (e.g., reading from local L1 caches). Never perform blocking I/O,
 * database queries, or network requests inside this method.
 */
@FunctionalInterface
public interface NametagProvider {

    /**
     * Resolves the nametag for a target player as observed by a viewer.
     *
     * @param viewer the player observing the nametag
     * @param target the target player whose nametag is being rendered
     * @return an Optional containing the custom nametag, or empty to fallback to registered/default tags
     */
    Optional<Nametag> provideNametag(Player viewer, Player target);
}
