package com.cotani.teleport.util;

import java.util.Objects;
import org.bukkit.Location;

public final class LocationUtils {

    private LocationUtils() {}

    public static boolean sameBlock(Location first, Location second) {
        return Objects.equals(first.getWorld(), second.getWorld())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    public static Location immutableCopy(Location location) {
        return location.clone();
    }
}
