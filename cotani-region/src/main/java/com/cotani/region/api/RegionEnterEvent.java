package com.cotani.region.api;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Event fired when a player enters a 3D region.
 *
 * @param player player who entered
 * @param region region that was entered
 * @param from previous location
 * @param to current location
 */
public record RegionEnterEvent(Player player, Region3D region, Location from, Location to) {

    public RegionEnterEvent {
        Objects.requireNonNull(player, "Parameter 'player' must not be null");
        Objects.requireNonNull(region, "Parameter 'region' must not be null");
        Objects.requireNonNull(from, "Parameter 'from' must not be null");
        Objects.requireNonNull(to, "Parameter 'to' must not be null");
    }
}
