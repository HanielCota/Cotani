package com.cotani.region.api;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Event fired when a player leaves a 3D region.
 *
 * @param player player who left
 * @param region region that was left
 * @param from previous location
 * @param to current location
 */
public record RegionLeaveEvent(Player player, Region3D region, Location from, Location to) {

    public RegionLeaveEvent {
        Objects.requireNonNull(player, "Parameter 'player' must not be null");
        Objects.requireNonNull(region, "Parameter 'region' must not be null");
        Objects.requireNonNull(from, "Parameter 'from' must not be null");
        Objects.requireNonNull(to, "Parameter 'to' must not be null");
    }
}
