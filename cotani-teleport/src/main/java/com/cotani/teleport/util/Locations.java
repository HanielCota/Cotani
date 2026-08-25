package com.cotani.teleport.util;

import java.util.Objects;
import org.bukkit.Location;

public final class Locations {
    private Locations() {}

    public static boolean sameBlock(Location first, Location second) {
        if (first == null || second == null) {
            return false;
        }

        return Objects.equals(first.getWorld(), second.getWorld())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    public static Location immutableCopy(Location location) {
        return Objects.requireNonNull(location, "location").clone();
    }
}
